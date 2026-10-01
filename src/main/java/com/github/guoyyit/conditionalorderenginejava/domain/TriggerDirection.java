package com.github.guoyyit.conditionalorderenginejava.domain;

/**
 * Comparison direction between the trigger reference price and the trigger price.
 * <ul>
 *     <li>{@link #ABOVE}: activates when reference price is greater than or equal to the trigger price.</li>
 *     <li>{@link #BELOW}: activates when reference price is less than or equal to the trigger price.</li>
 * </ul>
 */
public enum TriggerDirection {
    ABOVE,
    BELOW
}
