package com.github.guoyyit.conditionalorderenginejava.model;

import com.github.guoyyit.conditionalorderenginejava.domain.ExecutionType;
import com.github.guoyyit.conditionalorderenginejava.domain.OrderStatus;
import com.github.guoyyit.conditionalorderenginejava.domain.Side;

import java.math.BigDecimal;

/**
 * Mutable state of an ordinary order, whether placed directly or created by
 * activating a conditional order.
 */
public class OrdinaryOrder {

    private String id;
    private String accountId;
    private String symbol;
    private Side side;
    private ExecutionType executionType;
    private BigDecimal limitPrice;
    private long quantity;
    private long maxSlippageBps;
    private Long expireAt;
    private OrderStatus status;
    private long createdAt;
    private Long updatedAt;
    private Long firstSeenTickSeq;
    private String referencePrice;
    private String fillPrice;
    private Long filledAt;
    private Long filledTickSeq;
    private String conditionalOrderId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    public void setExecutionType(ExecutionType executionType) {
        this.executionType = executionType;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
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

    public Long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Long expireAt) {
        this.expireAt = expireAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getFirstSeenTickSeq() {
        return firstSeenTickSeq;
    }

    public void setFirstSeenTickSeq(Long firstSeenTickSeq) {
        this.firstSeenTickSeq = firstSeenTickSeq;
    }

    public String getReferencePrice() {
        return referencePrice;
    }

    public void setReferencePrice(String referencePrice) {
        this.referencePrice = referencePrice;
    }

    public String getFillPrice() {
        return fillPrice;
    }

    public void setFillPrice(String fillPrice) {
        this.fillPrice = fillPrice;
    }

    public Long getFilledAt() {
        return filledAt;
    }

    public void setFilledAt(Long filledAt) {
        this.filledAt = filledAt;
    }

    public Long getFilledTickSeq() {
        return filledTickSeq;
    }

    public void setFilledTickSeq(Long filledTickSeq) {
        this.filledTickSeq = filledTickSeq;
    }

    public String getConditionalOrderId() {
        return conditionalOrderId;
    }

    public void setConditionalOrderId(String conditionalOrderId) {
        this.conditionalOrderId = conditionalOrderId;
    }
}
