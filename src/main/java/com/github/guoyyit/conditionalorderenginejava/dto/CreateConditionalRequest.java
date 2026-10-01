package com.github.guoyyit.conditionalorderenginejava.dto;

import com.github.guoyyit.conditionalorderenginejava.domain.ConditionalType;
import com.github.guoyyit.conditionalorderenginejava.domain.ExecutionType;
import com.github.guoyyit.conditionalorderenginejava.domain.PriceSource;
import com.github.guoyyit.conditionalorderenginejava.domain.Side;
import com.github.guoyyit.conditionalorderenginejava.domain.TriggerDirection;

import java.math.BigDecimal;

/**
 * Request body for submitting a conditional order.
 */
public class CreateConditionalRequest {

    private String accountId;
    private String symbol;
    private Side side;
    private ConditionalType type;
    private TriggerDirection direction;
    private BigDecimal triggerPrice;
    private PriceSource priceSource;
    private ExecutionType executionType;
    private BigDecimal limitPrice;
    private Long quantity;
    private Long maxSlippageBps;
    private Long expireAt;

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

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public Long getMaxSlippageBps() {
        return maxSlippageBps;
    }

    public void setMaxSlippageBps(Long maxSlippageBps) {
        this.maxSlippageBps = maxSlippageBps;
    }

    public Long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Long expireAt) {
        this.expireAt = expireAt;
    }
}
