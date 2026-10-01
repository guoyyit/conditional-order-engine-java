package com.github.guoyyit.conditionalorderenginejava.store;

/**
 * One durable, append-only event record. Serves both crash recovery and audit.
 * All monetary values are stored as their canonical decimal string.
 */
public class LoggedEvent {

    private long seq;
    private long timestamp;
    private EventType type;
    private String conditionalOrderId;
    private String orderId;
    private String accountId;
    private String symbol;
    private String side;
    private String conditionalType;
    private String direction;
    private String priceSource;
    private String executionType;
    private Long expireAt;
    private Long firstSeenTickSeq;
    private long version;
    private long tickSeq;
    private String triggerPrice;
    private String referencePrice;
    private String executionPrice;
    private String fillPrice;
    private String limitPrice;
    private long quantity;
    private long maxSlippageBps;
    private long slipBps;
    private String reason;
    private String detail;

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

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
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

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getConditionalType() {
        return conditionalType;
    }

    public void setConditionalType(String conditionalType) {
        this.conditionalType = conditionalType;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getPriceSource() {
        return priceSource;
    }

    public void setPriceSource(String priceSource) {
        this.priceSource = priceSource;
    }

    public String getExecutionType() {
        return executionType;
    }

    public void setExecutionType(String executionType) {
        this.executionType = executionType;
    }

    public Long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Long expireAt) {
        this.expireAt = expireAt;
    }

    public Long getFirstSeenTickSeq() {
        return firstSeenTickSeq;
    }

    public void setFirstSeenTickSeq(Long firstSeenTickSeq) {
        this.firstSeenTickSeq = firstSeenTickSeq;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public long getTickSeq() {
        return tickSeq;
    }

    public void setTickSeq(long tickSeq) {
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

    public String getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(String limitPrice) {
        this.limitPrice = limitPrice;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getMaxSlippageBps() {
        return maxSlippageBps;
    }

    public void setMaxSlippageBps(long maxSlippageBps) {
        this.maxSlippageBps = maxSlippageBps;
    }

    public long getSlipBps() {
        return slipBps;
    }

    public void setSlipBps(long slipBps) {
        this.slipBps = slipBps;
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
