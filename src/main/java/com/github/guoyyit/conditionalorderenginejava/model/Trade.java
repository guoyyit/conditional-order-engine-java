package com.github.guoyyit.conditionalorderenginejava.model;

import java.math.BigDecimal;

/**
 * 成交记录。
 */
public record Trade(
        String tradeId,
        String orderId,
        String accountId,
        String symbol,
        Side side,
        BigDecimal price,
        long quantity,
        long timestamp) {
}
