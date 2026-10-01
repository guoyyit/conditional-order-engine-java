package com.github.guoyyit.conditionalorderenginejava.domain;

/**
 * Lifecycle status of a conditional order.
 */
public enum ConditionalStatus {
    PENDING,
    ACTIVATED,
    FILLED,
    CANCELLED,
    EXPIRED,
    REJECTED_SLIPPAGE
}
