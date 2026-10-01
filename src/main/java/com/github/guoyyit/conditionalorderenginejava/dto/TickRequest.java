package com.github.guoyyit.conditionalorderenginejava.dto;

import java.math.BigDecimal;

/**
 * Request body for injecting one local market data tick.
 * When {@code timestamp} is null the server assigns the current time.
 */
public class TickRequest {

    private String symbol;
    private Long timestamp;
    private BigDecimal last;
    private BigDecimal bid;
    private BigDecimal ask;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getLast() {
        return last;
    }

    public void setLast(BigDecimal last) {
        this.last = last;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public void setBid(BigDecimal bid) {
        this.bid = bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public void setAsk(BigDecimal ask) {
        this.ask = ask;
    }
}
