package com.github.guoyyit.conditionalorderenginejava.engine;

import com.github.guoyyit.conditionalorderenginejava.model.Trade;

/**
 * 执行结果：成交或滑点超限拒绝，二者可区分。
 */
public record ExecutionResult(boolean filled, Trade trade, String rejectReason) {

    public static ExecutionResult filled(Trade trade) {
        return new ExecutionResult(true, trade, null);
    }

    public static ExecutionResult slippageRejected(String reason) {
        return new ExecutionResult(false, null, reason);
    }

    /** 限价未触及，订单继续留在执行流程等待后续行情 */
    public static ExecutionResult pending() {
        return new ExecutionResult(false, null, null);
    }

    public boolean isTerminalReject() {
        return rejectReason != null;
    }
}
