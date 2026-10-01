package com.github.guoyyit.conditionalorderenginejava.engine;

import java.math.BigDecimal;

/**
 * Immutable outcome of evaluating one ordinary order against one tick.
 * The executor performs no mutation or persistence; the engine applies it.
 */
public final class ExecutionDecision {

    public enum Kind {
        FILLED,
        REJECTED_SLIPPAGE,
        EXPIRED,
        STILL_ACTIVE
    }

    private final Kind kind;
    private final BigDecimal fillPrice;
    private final long slipBps;
    private final String reason;

    private ExecutionDecision(Kind kind, BigDecimal fillPrice, long slipBps, String reason) {
        this.kind = kind;
        this.fillPrice = fillPrice;
        this.slipBps = slipBps;
        this.reason = reason;
    }

    public static ExecutionDecision filled(BigDecimal fillPrice, long slipBps) {
        return new ExecutionDecision(Kind.FILLED, fillPrice, slipBps, null);
    }

    public static ExecutionDecision rejectedSlippage(BigDecimal executionPrice, long slipBps, String reason) {
        return new ExecutionDecision(Kind.REJECTED_SLIPPAGE, executionPrice, slipBps, reason);
    }

    public static ExecutionDecision expired() {
        return new ExecutionDecision(Kind.EXPIRED, null, 0L, null);
    }

    public static ExecutionDecision stillActive() {
        return new ExecutionDecision(Kind.STILL_ACTIVE, null, 0L, null);
    }

    public Kind getKind() {
        return kind;
    }

    public BigDecimal getFillPrice() {
        return fillPrice;
    }

    public long getSlipBps() {
        return slipBps;
    }

    public String getReason() {
        return reason;
    }
}
