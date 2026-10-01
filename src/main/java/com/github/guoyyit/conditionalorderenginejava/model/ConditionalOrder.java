package com.github.guoyyit.conditionalorderenginejava.model;

import java.math.BigDecimal;

/**
 * 条件订单实体。激活前不占用资金或仓位。
 */
public class ConditionalOrder {

    private String orderId;
    private String accountId;
    private String symbol;
    private Side side;
    private OrderKind kind;
    private PriceSource priceSource;
    private CompareDirection direction;
    private BigDecimal triggerPrice;
    private ExecType execType;
    /** 限价执行时的限价价格；市价执行时为空 */
    private BigDecimal limitPrice;
    /** 市价执行时的滑点上限（绝对价格偏差），为空表示不约束 */
    private BigDecimal maxSlippage;
    private long quantity;
    /** 过期时间戳（毫秒），0 表示永不过期 */
    private long expireAt;
    private long createdAt;
    private long version;
    private ConditionalOrderStatus status;

    public ConditionalOrder() {
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

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public OrderKind getKind() {
        return kind;
    }

    public void setKind(OrderKind kind) {
        this.kind = kind;
    }

    public PriceSource getPriceSource() {
        return priceSource;
    }

    public void setPriceSource(PriceSource priceSource) {
        this.priceSource = priceSource;
    }

    public CompareDirection getDirection() {
        return direction;
    }

    public void setDirection(CompareDirection direction) {
        this.direction = direction;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(BigDecimal triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public ExecType getExecType() {
        return execType;
    }

    public void setExecType(ExecType execType) {
        this.execType = execType;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }

    public BigDecimal getMaxSlippage() {
        return maxSlippage;
    }

    public void setMaxSlippage(BigDecimal maxSlippage) {
        this.maxSlippage = maxSlippage;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public ConditionalOrderStatus getStatus() {
        return status;
    }

    public void setStatus(ConditionalOrderStatus status) {
        this.status = status;
    }
}
