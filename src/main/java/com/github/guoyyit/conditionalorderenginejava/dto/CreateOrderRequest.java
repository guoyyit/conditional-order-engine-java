package com.github.guoyyit.conditionalorderenginejava.dto;

import com.github.guoyyit.conditionalorderenginejava.domain.ExecutionType;
import com.github.guoyyit.conditionalorderenginejava.domain.Side;

import java.math.BigDecimal;

/**
 * Request body for direct order placement, used as the reference implementation
 * of post-activation execution.
 */
public class CreateOrderRequest {

    private String accountId;
    private String symbol;
    private Side side;
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
