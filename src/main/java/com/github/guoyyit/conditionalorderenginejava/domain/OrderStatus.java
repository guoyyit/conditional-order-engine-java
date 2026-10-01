package com.github.guoyyit.conditionalorderenginejava.domain;

/**
 * Lifecycle status of an ordinary (activated or directly placed) order.
 */
public enum OrderStatus {
    NEW,
    FILLED,
    CANCELLED,
    EXPIRED,
    REJECTED
}
