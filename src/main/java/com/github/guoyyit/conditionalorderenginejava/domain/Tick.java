package com.github.guoyyit.conditionalorderenginejava.domain;

import java.math.BigDecimal;

/**
 * A locally injected market data snapshot. Individual prices may be {@code null}
 * when the corresponding quote is not available in this snapshot.
 */
public class Tick {

    private String symbol;
    private long seq;
    private long timestamp;
    private BigDecimal last;
    private BigDecimal bid;
    private BigDecimal ask;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
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
