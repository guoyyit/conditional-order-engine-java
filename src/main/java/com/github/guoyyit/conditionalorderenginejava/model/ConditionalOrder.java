package com.github.guoyyit.conditionalorderenginejava.model;

import com.github.guoyyit.conditionalorderenginejava.domain.ConditionalStatus;
import com.github.guoyyit.conditionalorderenginejava.domain.ConditionalType;
import com.github.guoyyit.conditionalorderenginejava.domain.ExecutionType;
import com.github.guoyyit.conditionalorderenginejava.domain.PriceSource;
import com.github.guoyyit.conditionalorderenginejava.domain.Side;
import com.github.guoyyit.conditionalorderenginejava.domain.TriggerDirection;

import java.math.BigDecimal;

/**
 * Mutable state of a conditional (stop-loss / take-profit) order.
 */
public class ConditionalOrder {

    private String id;
    private long version;
    private String accountId;
    private String symbol;
    private Side side;
    private ConditionalType type;
    private TriggerDirection direction;
    private BigDecimal triggerPrice;
    private PriceSource priceSource;
    private ExecutionType executionType;
    private BigDecimal limitPrice;
    private long quantity;
    private long maxSlippageBps;
    private Long expireAt;
    private ConditionalStatus status;
    private long createdAt;
    private long updatedAt;
    private Long activatedAt;
    private long activatedTickSeq;
    private String activatedReferencePrice;
    private String childOrderId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
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

    public ConditionalType getType() {
        return type;
    }

    public void setType(ConditionalType type) {
        this.type = type;
    }

    public TriggerDirection getDirection() {
        return direction;
    }

    public void setDirection(TriggerDirection direction) {
        this.direction = direction;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(BigDecimal triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public PriceSource getPriceSource() {
        return priceSource;
    }

    public void setPriceSource(PriceSource priceSource) {
        this.priceSource = priceSource;
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

    public ConditionalStatus getStatus() {
        return status;
    }

    public void setStatus(ConditionalStatus status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Long activatedAt) {
        this.activatedAt = activatedAt;
    }

    public long getActivatedTickSeq() {
        return activatedTickSeq;
    }

    public void setActivatedTickSeq(long activatedTickSeq) {
        this.activatedTickSeq = activatedTickSeq;
    }

    public String getActivatedReferencePrice() {
        return activatedReferencePrice;
    }

    public void setActivatedReferencePrice(String activatedReferencePrice) {
        this.activatedReferencePrice = activatedReferencePrice;
    }

    public String getChildOrderId() {
        return childOrderId;
    }

    public void setChildOrderId(String childOrderId) {
        this.childOrderId = childOrderId;
    }
}
