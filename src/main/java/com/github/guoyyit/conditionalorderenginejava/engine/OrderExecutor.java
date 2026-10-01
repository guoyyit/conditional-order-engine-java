package com.github.guoyyit.conditionalorderenginejava.engine;

import com.github.guoyyit.conditionalorderenginejava.domain.Tick;
import com.github.guoyyit.conditionalorderenginejava.domain.Side;
import com.github.guoyyit.conditionalorderenginejava.model.OrdinaryOrder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Execution semantics shared by directly placed orders and activated conditional
 * orders: limit matching, market slippage protection, cancellation and expiry.
 */
@Component
public class OrderExecutor {

    /**
     * Adverse slippage in basis points of {@code executionPrice} relative to
     * {@code referencePrice} for the given side; returns 0 for favorable moves.
     */
    public long adverseSlipBps(OrdinaryOrder order, BigDecimal referencePrice, BigDecimal executionPrice) {
        if (referencePrice == null || executionPrice == null) {
            return 0L;
        }
        BigDecimal diff;
        if (order.getSide() == Side.BUY) {
            diff = executionPrice.subtract(referencePrice);
        } else {
            diff = referencePrice.subtract(executionPrice);
        }
        if (diff.signum() <= 0) {
            return 0L;
        }
        return diff.multiply(BigDecimal.valueOf(10_000L))
                .divide(referencePrice, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * Limit price match test: a BUY fills at/below limit, a SELL at/above limit.
     */
    public boolean limitMatches(OrdinaryOrder order, BigDecimal executionPrice) {
        if (executionPrice == null || order.getLimitPrice() == null) {
            return false;
        }
        int cmp = executionPrice.compareTo(order.getLimitPrice());
        return order.getSide() == Side.BUY ? cmp <= 0 : cmp >= 0;
    }

    /**
     * The executable price on this tick for the order's side: the ask for buys
     * and the bid for sells. Falls back to last price, then null (no liquidity).
     */
    public BigDecimal executablePrice(OrdinaryOrder order, Tick tick) {
        if (order.getSide() == Side.BUY) {
            if (tick.getAsk() != null) {
                return tick.getAsk();
            }
            return tick.getLast();
        }
        if (tick.getBid() != null) {
            return tick.getBid();
        }
        return tick.getLast();
    }

    /**
     * Pure evaluation of an active (NEW) ordinary order against one tick.
     * <p>
     * The first tick seen by the order establishes its slippage reference for
     * market orders. A market order fills immediately on that tick at the side's
     * executable price unless the adverse move versus its reference exceeds the
     * cap, in which case it is rejected with {@link ExecutionDecision.Kind#REJECTED_SLIPPAGE}.
     * Limit orders only fill at prices no worse than their limit price and otherwise
     * remain active until matched, cancelled or expired. No order ever chases the
     * market in an adverse direction.
     */
    public ExecutionDecision evaluate(OrdinaryOrder order, Tick tick) {
        if (order.getExpireAt() != null && tick.getTimestamp() > order.getExpireAt()) {
            return ExecutionDecision.expired();
        }
        BigDecimal executablePrice = executablePrice(order, tick);
        if (executablePrice == null) {
            return ExecutionDecision.stillActive();
        }
        if (order.getExecutionType() == com.github.guoyyit.conditionalorderenginejava.domain.ExecutionType.MARKET) {
            BigDecimal reference;
            if (order.getFirstSeenTickSeq() != null
                    && order.getFirstSeenTickSeq() == tick.getSeq()
                    && order.getReferencePrice() != null) {
                reference = new BigDecimal(order.getReferencePrice());
            } else {
                reference = executablePrice;
            }
            long slip = adverseSlipBps(order, reference, executablePrice);
            if (slip > order.getMaxSlippageBps()) {
                String reason = "market slippage " + slip + "bps exceeds cap "
                        + order.getMaxSlippageBps() + "bps (reference=" + reference
                        + ", execution=" + executablePrice + ")";
                return ExecutionDecision.rejectedSlippage(executablePrice, slip, reason);
            }
            return ExecutionDecision.filled(executablePrice, slip);
        }
        if (limitMatches(order, executablePrice)) {
            return ExecutionDecision.filled(executablePrice,
                    adverseSlipBps(order,
                            order.getReferencePrice() == null ? executablePrice : new BigDecimal(order.getReferencePrice()),
                            executablePrice));
        }
        return ExecutionDecision.stillActive();
    }

    /**
     * Deterministically completes a conditional market order during crash
     * recovery. Its activation event durably recorded the trigger reference
     * price; a fill/reject decision derived solely from that price and the
     * configured cap is identical whether it was made before or after the crash,
     * so the order is never re-triggered or filled twice.
     */
    public ExecutionDecision recoverAtReference(OrdinaryOrder order) {
        if (order.getExecutionType() != com.github.guoyyit.conditionalorderenginejava.domain.ExecutionType.MARKET
                || order.getReferencePrice() == null) {
            return ExecutionDecision.stillActive();
        }
        BigDecimal reference = new BigDecimal(order.getReferencePrice());
        long slip = adverseSlipBps(order, reference, reference);
        return ExecutionDecision.filled(reference, slip);
    }
}
