package com.github.guoyyit.conditionalorderenginejava.tests;

import com.github.guoyyit.conditionalorderenginejava.domain.ExecutionType;
import com.github.guoyyit.conditionalorderenginejava.domain.OrderStatus;
import com.github.guoyyit.conditionalorderenginejava.domain.PriceSource;
import com.github.guoyyit.conditionalorderenginejava.domain.Side;
import com.github.guoyyit.conditionalorderenginejava.domain.Tick;
import com.github.guoyyit.conditionalorderenginejava.domain.TriggerDirection;
import com.github.guoyyit.conditionalorderenginejava.engine.ExecutionDecision;
import com.github.guoyyit.conditionalorderenginejava.engine.OrderExecutor;
import com.github.guoyyit.conditionalorderenginejava.engine.TriggerEvaluator;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.OrdinaryOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerAndExecutionTest {

    private final TriggerEvaluator evaluator = new TriggerEvaluator();
    private final OrderExecutor executor = new OrderExecutor();

    @Test
    void referencePriceFollowsConfiguredSource() {
        Tick tick = tick("100", "99", "101");
        assertEquals(new BigDecimal("100"), evaluator.referencePrice(tick, PriceSource.LAST));
        assertEquals(new BigDecimal("99"), evaluator.referencePrice(tick, PriceSource.BID));
        assertEquals(new BigDecimal("101"), evaluator.referencePrice(tick, PriceSource.ASK));
    }

    @Test
    void missingQuoteMeansNoTrigger() {
        Tick tick = tick("100", null, null);
        ConditionalOrder order = conditional(TriggerDirection.BELOW, "101", PriceSource.BID);
        assertNull(evaluator.referencePrice(tick, PriceSource.BID));
        assertFalse(evaluator.isTriggered(order, evaluator.referencePrice(tick, PriceSource.BID)));
    }

    @Test
    void crossAboveAndBelowAreInclusive() {
        assertTrue(evaluator.matches(TriggerDirection.ABOVE, new BigDecimal("100"), new BigDecimal("100")));
        assertTrue(evaluator.matches(TriggerDirection.BELOW, new BigDecimal("100"), new BigDecimal("100")));
        assertTrue(evaluator.matches(TriggerDirection.ABOVE, new BigDecimal("101"), new BigDecimal("100")));
        assertFalse(evaluator.matches(TriggerDirection.ABOVE, new BigDecimal("99"), new BigDecimal("100")));
        assertTrue(evaluator.matches(TriggerDirection.BELOW, new BigDecimal("99"), new BigDecimal("100")));
    }

    @Test
    void marketBuyWithAdverseGapBeyondCapIsRejectedNotChased() {
        OrdinaryOrder order = marketOrder(Side.BUY, 50L);
        order.setFirstSeenTickSeq(1L);
        order.setReferencePrice("100");
        Tick gap = tick("101", null, "101");
        gap.setSeq(1L);

        ExecutionDecision decision = executor.evaluate(order, gap);

        assertEquals(ExecutionDecision.Kind.REJECTED_SLIPPAGE, decision.getKind());
        assertEquals(100L, decision.getSlipBps());
    }

    @Test
    void marketBuyWithinCapFillsAtAvailablePrice() {
        OrdinaryOrder order = marketOrder(Side.BUY, 100L);
        order.setFirstSeenTickSeq(1L);
        order.setReferencePrice("100");
        Tick tick = tick("100.5", null, "100.5");
        tick.setSeq(1L);

        ExecutionDecision decision = executor.evaluate(order, tick);

        assertEquals(ExecutionDecision.Kind.FILLED, decision.getKind());
        assertEquals(new BigDecimal("100.5"), decision.getFillPrice());
        assertEquals(50L, decision.getSlipBps());
    }

    @Test
    void marketSellAdverseDirectionIsBidBelowReference() {
        OrdinaryOrder order = marketOrder(Side.SELL, 50L);
        order.setFirstSeenTickSeq(1L);
        order.setReferencePrice("100");
        Tick tick = tick("99", "99", null);
        tick.setSeq(1L);

        ExecutionDecision decision = executor.evaluate(order, tick);

        assertEquals(ExecutionDecision.Kind.REJECTED_SLIPPAGE, decision.getKind());
        assertEquals(100L, decision.getSlipBps());
    }

    @Test
    void limitBuyNeverFillsAboveLimitAndLimitSellNeverBelow() {
        OrdinaryOrder buy = limitOrder(Side.BUY, "100");
        assertFalse(executor.limitMatches(buy, new BigDecimal("100.01")));
        assertTrue(executor.limitMatches(buy, new BigDecimal("100")));

        OrdinaryOrder sell = limitOrder(Side.SELL, "100");
        assertFalse(executor.limitMatches(sell, new BigDecimal("99.99")));
        assertTrue(executor.limitMatches(sell, new BigDecimal("100")));
    }

    @Test
    void expiredOrderProducesExpiredDecision() {
        OrdinaryOrder order = limitOrder(Side.BUY, "100");
        order.setExpireAt(1000L);
        Tick tick = tick("99", "99", "99");
        tick.setTimestamp(1001L);
        assertEquals(ExecutionDecision.Kind.EXPIRED, executor.evaluate(order, tick).getKind());
    }

    private static Tick tick(String last, String bid, String ask) {
        Tick tick = new Tick();
        tick.setSymbol("BTC");
        tick.setTimestamp(1L);
        tick.setLast(last == null ? null : new BigDecimal(last));
        tick.setBid(bid == null ? null : new BigDecimal(bid));
        tick.setAsk(ask == null ? null : new BigDecimal(ask));
        return tick;
    }

    private static ConditionalOrder conditional(TriggerDirection direction, String trigger, PriceSource source) {
        ConditionalOrder order = new ConditionalOrder();
        order.setSymbol("BTC");
        order.setDirection(direction);
        order.setTriggerPrice(new BigDecimal(trigger));
        order.setPriceSource(source);
        return order;
    }

    private static OrdinaryOrder marketOrder(Side side, long capBps) {
        OrdinaryOrder order = new OrdinaryOrder();
        order.setSymbol("BTC");
        order.setSide(side);
        order.setExecutionType(ExecutionType.MARKET);
        order.setQuantity(1);
        order.setMaxSlippageBps(capBps);
        order.setStatus(OrderStatus.NEW);
        return order;
    }

    private static OrdinaryOrder limitOrder(Side side, String limit) {
        OrdinaryOrder order = new OrdinaryOrder();
        order.setSymbol("BTC");
        order.setSide(side);
        order.setExecutionType(ExecutionType.LIMIT);
        order.setLimitPrice(new BigDecimal(limit));
        order.setQuantity(1);
        order.setStatus(OrderStatus.NEW);
        return order;
    }
}
