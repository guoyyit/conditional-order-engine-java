package com.github.guoyyit.conditionalorderenginejava.store;

import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.Trade;
import com.github.guoyyit.conditionalorderenginejava.model.TriggerRecord;

/**
 * 持久化事件：条件订单的全部状态变更以事件形式追加写入日志，崩溃后重放恢复。
 */
public record OrderEvent(
        EventType type,
        ConditionalOrder order,
        Trade trade,
        TriggerRecord triggerRecord,
        long timestamp) {

    public enum EventType {
        SUBMITTED,
        MODIFIED,
        CANCELLED,
        ACTIVATED,
        FILLED,
        EXPIRED,
        SLIPPAGE_REJECTED
    }

    public static OrderEvent of(EventType type, ConditionalOrder order) {
        return new OrderEvent(type, order, null, null, System.currentTimeMillis());
    }

    public static OrderEvent filled(ConditionalOrder order, Trade trade, TriggerRecord record) {
        return new OrderEvent(EventType.FILLED, order, trade, record, System.currentTimeMillis());
    }

    public static OrderEvent activated(ConditionalOrder order, TriggerRecord record) {
        return new OrderEvent(EventType.ACTIVATED, order, null, record, System.currentTimeMillis());
    }
}
