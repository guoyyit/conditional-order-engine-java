package com.github.guoyyit.conditionalorderenginejava.dto;

/**
 * Audit view of a trigger / execution event, explaining the deviation between
 * trigger price, trigger reference price and actual fill price.
 */
public class TriggerHistoryView {

    private long eventSeq;
    private long timestamp;
    private String type;
    private String conditionalOrderId;
    private String orderId;
    private String symbol;
    private Long tickSeq;
    private String triggerPrice;
    private String referencePrice;
    private String executionPrice;
    private String fillPrice;
    private Long maxSlippageBps;
    private Long actualSlipBps;
    private String reason;
    private String detail;

    public long getEventSeq() {
        return eventSeq;
    }

    public void setEventSeq(long eventSeq) {
        this.eventSeq = eventSeq;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getConditionalOrderId() {
        return conditionalOrderId;
    }

    public void setConditionalOrderId(String conditionalOrderId) {
        this.conditionalOrderId = conditionalOrderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Long getTickSeq() {
        return tickSeq;
    }

    public void setTickSeq(Long tickSeq) {
        this.tickSeq = tickSeq;
    }

    public String getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(String triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public String getReferencePrice() {
        return referencePrice;
    }

    public void setReferencePrice(String referencePrice) {
        this.referencePrice = referencePrice;
    }

    public String getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(String executionPrice) {
        this.executionPrice = executionPrice;
    }

    public String getFillPrice() {
        return fillPrice;
    }

    public void setFillPrice(String fillPrice) {
        this.fillPrice = fillPrice;
    }

    public Long getMaxSlippageBps() {
        return maxSlippageBps;
    }

    public void setMaxSlippageBps(Long maxSlippageBps) {
        this.maxSlippageBps = maxSlippageBps;
    }

    public Long getActualSlipBps() {
        return actualSlipBps;
    }

    public void setActualSlipBps(Long actualSlipBps) {
        this.actualSlipBps = actualSlipBps;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
