package com.github.guoyyit.conditionalorderenginejava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.conditionalorderenginejava.config.EngineProperties;
import com.github.guoyyit.conditionalorderenginejava.domain.ConditionalStatus;
import com.github.guoyyit.conditionalorderenginejava.domain.ConditionalType;
import com.github.guoyyit.conditionalorderenginejava.domain.ExecutionType;
import com.github.guoyyit.conditionalorderenginejava.domain.OrderStatus;
import com.github.guoyyit.conditionalorderenginejava.domain.PriceSource;
import com.github.guoyyit.conditionalorderenginejava.domain.Side;
import com.github.guoyyit.conditionalorderenginejava.domain.Tick;
import com.github.guoyyit.conditionalorderenginejava.domain.TriggerDirection;
import com.github.guoyyit.conditionalorderenginejava.dto.CancelRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.CreateConditionalRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.CreateOrderRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.ModifyConditionalRequest;
import com.github.guoyyit.conditionalorderenginejava.engine.ConflictException;
import com.github.guoyyit.conditionalorderenginejava.engine.OrderExecutor;
import com.github.guoyyit.conditionalorderenginejava.engine.TradingEngine;
import com.github.guoyyit.conditionalorderenginejava.engine.TriggerEvaluator;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.OrdinaryOrder;
import com.github.guoyyit.conditionalorderenginejava.store.FileEventStore;
import com.github.guoyyit.conditionalorderenginejava.store.InMemoryOrderStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineFlowTest {

    @TempDir
    Path tempDir;

    private TradingEngine newEngine() {
        EngineProperties properties = new EngineProperties();
        properties.setDefaultPriceSource(PriceSource.LAST);
        properties.setDefaultMaxSlippageBps(50L);
        FileEventStore eventStore = new FileEventStore(
                new ObjectMapper(), tempDir.resolve("events.log"), true);
        return new TradingEngine(new InMemoryOrderStore(), eventStore, properties,
                new TriggerEvaluator(), new OrderExecutor());
    }

    private static CreateConditionalRequest stopOrder(String trigger, TriggerDirection direction,
                                                      ExecutionType executionType, String limit) {
        CreateConditionalRequest request = new CreateConditionalRequest();
        request.setAccountId("acct-1");
        request.setSymbol("BTC");
        request.setSide(Side.SELL);
        request.setType(ConditionalType.STOP_LOSS);
        request.setDirection(direction);
        request.setTriggerPrice(new BigDecimal(trigger));
        request.setPriceSource(PriceSource.LAST);
        request.setExecutionType(executionType);
        if (limit != null) {
            request.setLimitPrice(new BigDecimal(limit));
        }
        request.setQuantity(2L);
        request.setMaxSlippageBps(50L);
        return request;
    }

    private Tick tick(String last, long timestamp) {
        Tick tick = new Tick();
        tick.setSymbol("BTC");
        tick.setTimestamp(timestamp);
        tick.setLast(new BigDecimal(last));
        tick.setBid(new BigDecimal(last));
        tick.setAsk(new BigDecimal(last));
        return tick;
    }

    @Test
    void activatesOnFirstAvailablePriceAfterCrossingAndFillsOnce() {
        TradingEngine engine = newEngine();
        ConditionalOrder order = engine.createConditional(
                stopOrder("100", TriggerDirection.BELOW, ExecutionType.MARKET, null));

        assertEquals(ConditionalStatus.PENDING, order.getStatus());

        engine.injectTick(tick("105", 10));
        assertEquals(ConditionalStatus.PENDING, engine.getConditional(order.getId()).getStatus());

        engine.injectTick(tick("98", 20));
        ConditionalOrder after = engine.getConditional(order.getId());
        assertEquals(ConditionalStatus.FILLED, after.getStatus());
        assertEquals(2L, after.getActivatedTickSeq());
        assertEquals("98", after.getActivatedReferencePrice());

        OrdinaryOrder child = engine.getOrder(after.getChildOrderId());
        assertEquals(OrderStatus.FILLED, child.getStatus());
        assertEquals("98", child.getFillPrice());

        engine.injectTick(tick("90", 30));
        assertEquals(OrderStatus.FILLED, engine.getOrder(child.getId()).getStatus());
        assertEquals(1, engine.history("acct-1", "BTC").stream()
                .filter(e -> "ORDER_FILLED".equals(e.getType())).count());
    }

    @Test
    void limitActivationBehavesLikeDirectLimitOrder() {
        TradingEngine engine = newEngine();
        ConditionalOrder order = engine.createConditional(
                stopOrder("100", TriggerDirection.BELOW, ExecutionType.LIMIT, "102"));
        engine.injectTick(tick("98", 10));

        OrdinaryOrder child = engine.getOrder(engine.getConditional(order.getId()).getChildOrderId());
        assertEquals(OrderStatus.NEW, child.getStatus());
        assertEquals(ConditionalStatus.ACTIVATED, engine.getConditional(order.getId()).getStatus());

        Tick up = tick("102", 20);
        engine.injectTick(up);
        assertEquals(OrderStatus.FILLED, engine.getOrder(child.getId()).getStatus());
        assertEquals("102", engine.getOrder(child.getId()).getFillPrice());

        CreateOrderRequest direct = new CreateOrderRequest();
        direct.setAccountId("acct-1");
        direct.setSymbol("BTC");
        direct.setSide(Side.SELL);
        direct.setExecutionType(ExecutionType.LIMIT);
        direct.setLimitPrice(new BigDecimal("103"));
        direct.setQuantity(2L);
        OrdinaryOrder reference = engine.placeOrder(direct);
        engine.injectTick(tick("100", 30));
        assertEquals(OrderStatus.NEW, engine.getOrder(reference.getId()).getStatus());
        engine.injectTick(tick("102", 40));
        assertEquals(OrderStatus.NEW, engine.getOrder(reference.getId()).getStatus());
        engine.injectTick(tick("103", 50));
        assertEquals(OrderStatus.FILLED, engine.getOrder(reference.getId()).getStatus());
    }

    @Test
    void marketSlippageBreachGivesDistinguishableResult() {
        TradingEngine engine = newEngine();
        ConditionalOrder order = engine.createConditional(
                stopOrder("100", TriggerDirection.BELOW, ExecutionType.MARKET, null));
        Tick gap = new Tick();
        gap.setSymbol("BTC");
        gap.setTimestamp(10L);
        gap.setLast(new BigDecimal("99"));
        gap.setBid(new BigDecimal("98"));
        gap.setAsk(new BigDecimal("99"));
        engine.injectTick(gap);

        assertEquals(ConditionalStatus.REJECTED_SLIPPAGE, engine.getConditional(order.getId()).getStatus());
        OrdinaryOrder child = engine.getOrder(engine.getConditional(order.getId()).getChildOrderId());
        assertEquals(OrderStatus.REJECTED, child.getStatus());
        long rejects = engine.history("acct-1", "BTC").stream()
                .filter(e -> "ORDER_REJECTED".equals(e.getType())
                        && "SLIPPAGE_EXCEEDED".equals(e.getReason()))
                .count();
        assertEquals(1, rejects);
    }

    @Test
    void modifyTakesEffectBeforeTriggerAndBumpsVersion() {
        TradingEngine engine = newEngine();
        ConditionalOrder order = engine.createConditional(
                stopOrder("100", TriggerDirection.BELOW, ExecutionType.MARKET, null));

        ModifyConditionalRequest modify = new ModifyConditionalRequest();
        modify.setTriggerPrice(new BigDecimal("90"));
        modify.setExpectedVersion(1L);
        ConditionalOrder modified = engine.modifyConditional(order.getId(), modify);
        assertEquals(2L, modified.getVersion());

        engine.injectTick(tick("95", 10));
        assertEquals(ConditionalStatus.PENDING, engine.getConditional(order.getId()).getStatus());
        engine.injectTick(tick("89", 20));
        assertEquals(ConditionalStatus.FILLED, engine.getConditional(order.getId()).getStatus());
    }

    @Test
    void staleVersionModifyIsRejected() {
        TradingEngine engine = newEngine();
        ConditionalOrder order = engine.createConditional(
                stopOrder("100", TriggerDirection.BELOW, ExecutionType.MARKET, null));
        ModifyConditionalRequest modify = new ModifyConditionalRequest();
        modify.setTriggerPrice(new BigDecimal("90"));
        modify.setExpectedVersion(99L);
        assertThrows(ConflictException.class, () -> engine.modifyConditional(order.getId(), modify));
    }

    @Test
    void cancelOrTriggerIsDeterministicUnderConcurrency() throws Exception {
        for (int iteration = 0; iteration < 30; iteration++) {
            TradingEngine engine = newEngine();
            ConditionalOrder order = engine.createConditional(
                    stopOrder("100", TriggerDirection.BELOW, ExecutionType.MARKET, null));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            AtomicInteger cancels = new AtomicInteger();

            Runnable cancel = () -> {
                try {
                    start.await();
                    engine.cancelConditional(order.getId(), new CancelRequest());
                    cancels.incrementAndGet();
                } catch (ConflictException expected) {
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            Runnable trigger = () -> {
                try {
                    start.await();
                    engine.injectTick(tick("98", 10));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            pool.submit(cancel);
            pool.submit(trigger);
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

            ConditionalStatus finalStatus = engine.getConditional(order.getId()).getStatus();
            assertTrue(finalStatus == ConditionalStatus.CANCELLED
                    || finalStatus == ConditionalStatus.FILLED);
            if (cancels.get() == 1) {
                assertEquals(ConditionalStatus.CANCELLED, finalStatus);
            } else {
                assertEquals(ConditionalStatus.FILLED, finalStatus);
            }
        }
    }

    @Test
    void cancelAfterActivationIsRejectedAndChildCanBeCancelledInstead() {
        TradingEngine engine = newEngine();
        ConditionalOrder order = engine.createConditional(
                stopOrder("100", TriggerDirection.BELOW, ExecutionType.LIMIT, "102"));
        engine.injectTick(tick("98", 10));

        assertEquals(ConditionalStatus.ACTIVATED, engine.getConditional(order.getId()).getStatus());
        assertThrows(ConflictException.class,
                () -> engine.cancelConditional(order.getId(), new CancelRequest()));

        OrdinaryOrder child = engine.getOrder(engine.getConditional(order.getId()).getChildOrderId());
        engine.cancelOrder(child.getId(), new CancelRequest());
        assertEquals(OrderStatus.CANCELLED, engine.getOrder(child.getId()).getStatus());
    }

    @Test
    void historyExplainsTriggerReferenceAndFill() {
        TradingEngine engine = newEngine();
        ConditionalOrder order = engine.createConditional(
                stopOrder("100", TriggerDirection.BELOW, ExecutionType.MARKET, null));
        engine.injectTick(tick("98", 10));
        assertNotNull(engine.history("acct-1", "BTC").stream()
                .filter(e -> "CONDITIONAL_ACTIVATED".equals(e.getType())
                        && "100".equals(e.getTriggerPrice())
                        && "98".equals(e.getReferencePrice()))
                .findAny().orElse(null));
        assertNotNull(engine.history("acct-1", "BTC").stream()
                .filter(e -> "ORDER_FILLED".equals(e.getType()) && "98".equals(e.getFillPrice()))
                .findAny().orElse(null));
        assertTrue(engine.history("other", "BTC").isEmpty());
    }
}
