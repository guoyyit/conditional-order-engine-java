package com.github.guoyyit.conditionalorderenginejava.store;

/**
 * Types of records in the durable append-only audit/event log.
 */
public enum EventType {
    CONDITIONAL_CREATED,
    CONDITIONAL_MODIFIED,
    CONDITIONAL_CANCELLED,
    CONDITIONAL_EXPIRED,
    CONDITIONAL_ACTIVATED,
    ORDER_PLACED,
    ORDER_FILLED,
    ORDER_CANCELLED,
    ORDER_EXPIRED,
    ORDER_REJECTED,
    MARKET_TICK
}
