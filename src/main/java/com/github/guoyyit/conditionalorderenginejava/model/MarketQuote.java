package com.github.guoyyit.conditionalorderenginejava.model;

import java.math.BigDecimal;

/**
 * 本地注入的行情快照：最新成交价、买一价、卖一价。
 */
public record MarketQuote(
        String symbol,
        BigDecimal lastTradePrice,
        BigDecimal bid1,
        BigDecimal ask1,
        long timestamp) {

    public BigDecimal priceOf(PriceSource source) {
        return switch (source) {
            case LAST_TRADE -> lastTradePrice;
            case BID1 -> bid1;
            case ASK1 -> ask1;
        };
    }
}
