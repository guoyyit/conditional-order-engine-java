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
import com.github.guoyyit.conditionalorderenginejava.dto.CreateConditionalRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.ModifyConditionalRequest;
import com.github.guoyyit.conditionalorderenginejava.engine.OrderExecutor;
import com.github.guoyyit.conditionalorderenginejava.engine.StateRecovery;
import com.github.guoyyit.conditionalorderenginejava.engine.TriggerEvaluator;
import com.github.guoyyit.conditionalorderenginejava.engine.TradingEngine;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.store.EventType;
import com.github.guoyyit.conditionalorderenginejava.store.FileEventStore;
import com.github.guoyyit.conditionalorderenginejava.store.InMemoryOrderStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryTest {

    @TempDir
    Path tempDir;

    private FileEventStore eventStore(boolean fsync) {
        return new FileEventStore(new ObjectMapper(), tempDir.resolve("events.log"), fsync);
    }

    private EngineProperties properties() {
        EngineProperties properties = new EngineProperties();
        properties.setDefaultPriceSource(PriceSource.LAST);
        properties.setDefaultMaxSlippageBps(50L);
        return properties;
    }

    private TradingEngine engineWith(FileEventStore store) {
        InMemoryOrderStore state = new InMemoryOrderStore();
        new StateRecovery(store, state).recover();
        return new TradingEngine(state, store, properties(), new TriggerEvaluator(), new OrderExecutor());
    }

    private CreateConditionalRequest request(String trigger, ExecutionType executionType, String limit) {
        CreateConditionalRequest request = new CreateConditionalRequest();
        request.setAccountId("acct-1");
        request.setSymbol("BTC");
        request.setSide(Side.SELL);
        request.setType(ConditionalType.STOP_LOSS);
        request.setDirection(TriggerDirection.BELOW);
        request.setTriggerPrice(new BigDecimal(trigger));
        request.setPriceSource(PriceSource.LAST);
        request.setExecutionType(executionType);
        if (limit != null) {
            request.setLimitPrice(new BigDecimal(limit));
        }
        request.setQuantity(1L);
        request.setMaxSlippageBps(50L);
        return request;
    }

    private Tick tick(String price, long timestamp) {
        Tick tick = new Tick();
        tick.setSymbol("BTC");
        tick.setTimestamp(timestamp);
        tick.setLast(new BigDecimal(price));
        tick.setBid(new BigDecimal(price));
        tick.setAsk(new BigDecimal(price));
        return tick;
    }

    @Test
    void restoresPendingModifiedActivatedAndFilledStates() {
        FileEventStore store = eventStore(true);
        TradingEngine engine = engineWith(store);

        ConditionalOrder pending = engine.createConditional(
                request("90", ExecutionType.MARKET, null));

        ConditionalOrder modified = engine.createConditional(
                request("100", ExecutionType.MARKET, null));
        ModifyConditionalRequest modify = new ModifyConditionalRequest();
        modify.setTriggerPrice(new BigDecimal("80"));
        engine.modifyConditional(modified.getId(), modify);

        ConditionalOrder filled = engine.createConditional(
                request("100", ExecutionType.MARKET, null));
        engine.injectTick(tick("98", 10));

        ConditionalOrder limitActive = engine.createConditional(
                request("100", ExecutionType.LIMIT, "102"));
        engine.injectTick(tick("98", 20));

        TradingEngine restored = engineWith(eventStore(true));

        assertEquals(ConditionalStatus.PENDING, restored.getConditional(pending.getId()).getStatus());

        ConditionalOrder restoredModified = restored.getConditional(modified.getId());
        assertEquals(ConditionalStatus.PENDING, restoredModified.getStatus());
        assertEquals(2L, restoredModified.getVersion());
        assertEquals(new BigDecimal("80"), restoredModified.getTriggerPrice());

        ConditionalOrder restoredFilled = restored.getConditional(filled.getId());
        assertEquals(ConditionalStatus.FILLED, restoredFilled.getStatus());
        assertEquals("98", restoredFilled.getActivatedReferencePrice());
        assertEquals(OrderStatus.FILLED, restored.getOrder(restoredFilled.getChildOrderId()).getStatus());
        assertEquals("98", restored.getOrder(restoredFilled.getChildOrderId()).getFillPrice());

        ConditionalOrder restoredActive = restored.getConditional(limitActive.getId());
        assertEquals(ConditionalStatus.ACTIVATED, restoredActive.getStatus());
        assertEquals(OrderStatus.NEW, restored.getOrder(restoredActive.getChildOrderId()).getStatus());
    }

    @Test
    void crashBetweenActivationAndFillCompletesMarketOrderOnce() throws Exception {
        FileEventStore store = eventStore(true);
        TradingEngine engine = engineWith(store);
        ConditionalOrder order = engine.createConditional(
                request("100", ExecutionType.MARKET, null));

        engine.injectTick(tick("98", 10));

        Path logFile = tempDir.resolve("events.log");
        List<String> lines = Files.readAllLines(logFile);
        int cutAfter = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("\"type\":\"ORDER_PLACED\"")) {
                cutAfter = i + 1;
            }
        }
        Files.write(logFile, lines.subList(0, cutAfter));

        TradingEngine restored = engineWith(eventStore(true));
        ConditionalOrder restoredOrder = restored.getConditional(order.getId());
        assertEquals(ConditionalStatus.FILLED, restoredOrder.getStatus());
        assertEquals(OrderStatus.FILLED, restored.getOrder(restoredOrder.getChildOrderId()).getStatus());
        assertEquals("98", restored.getOrder(restoredOrder.getChildOrderId()).getFillPrice());

        TradingEngine restoredAgain = engineWith(eventStore(true));
        long fills = restoredAgain.history("acct-1", "BTC").stream()
                .filter(e -> "ORDER_FILLED".equals(e.getType())).count();
        assertEquals(1, fills);
    }

    @Test
    void cancelledOrdersStayCancelledAfterRestart() {
        FileEventStore store = eventStore(true);
        TradingEngine engine = engineWith(store);
        ConditionalOrder order = engine.createConditional(
                request("100", ExecutionType.MARKET, null));
        engine.cancelConditional(order.getId(), null);

        TradingEngine restored = engineWith(eventStore(true));
        assertEquals(ConditionalStatus.CANCELLED, restored.getConditional(order.getId()).getStatus());

        restored.injectTick(tick("98", 10));
        assertEquals(ConditionalStatus.CANCELLED, restored.getConditional(order.getId()).getStatus());
    }

    @Test
    void tickSequenceContinuesAfterRestart() {
        FileEventStore store = eventStore(true);
        TradingEngine engine = engineWith(store);
        ConditionalOrder order = engine.createConditional(
                request("100", ExecutionType.MARKET, null));
        engine.injectTick(tick("101", 10));

        TradingEngine restored = engineWith(eventStore(true));
        restored.injectTick(tick("98", 20));
        assertEquals(2L, restored.getConditional(order.getId()).getActivatedTickSeq());

        TradingEngine restoredAgain = engineWith(eventStore(true));
        restoredAgain.injectTick(tick("97", 30));
        assertEquals(2L, restoredAgain.getConditional(order.getId()).getActivatedTickSeq());
    }
}
