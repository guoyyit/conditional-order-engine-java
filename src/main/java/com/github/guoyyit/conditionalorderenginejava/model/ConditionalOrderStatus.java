package com.github.guoyyit.conditionalorderenginejava.model;

/**
 * 条件订单生命周期状态。
 */
public enum ConditionalOrderStatus {
    /** 待触发，可修改可取消，不占用资金仓位 */
    PENDING,
    /** 已激活，已转为普通订单进入执行流程 */
    ACTIVATED,
    /** 已成交 */
    FILLED,
    /** 已取消（激活前用户取消，或激活后撤单） */
    CANCELLED,
    /** 已过期 */
    EXPIRED,
    /** 市价执行滑点超限，执行被拒绝 */
    SLIPPAGE_REJECTED
}
