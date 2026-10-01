package com.github.guoyyit.conditionalorderenginejava.model;

import java.math.BigDecimal;

/**
 * 触发历史记录：触发时间、判定价格、实际成交价格及偏差，供审计与查询。
 */
public record TriggerRecord(
        String orderId,
        String accountId,
        String symbol,
        long triggeredAt,
        PriceSource priceSource,
        BigDecimal judgedPrice,
        BigDecimal triggerPrice,
        BigDecimal execPrice,
        String outcome,
        String detail) {

    /** 判定价格相对触发价的偏差 */
    public BigDecimal judgeDeviation() {
        if (judgedPrice == null || triggerPrice == null) {
            return null;
        }
        return judgedPrice.subtract(triggerPrice);
    }

    /** 实际成交价格相对判定价格的偏差（滑点） */
    public BigDecimal execDeviation() {
        if (execPrice == null || judgedPrice == null) {
            return null;
        }
        return execPrice.subtract(judgedPrice);
    }
}
