package com.github.guoyyit.conditionalorderenginejava.engine;

import com.github.guoyyit.conditionalorderenginejava.domain.PriceSource;
import com.github.guoyyit.conditionalorderenginejava.domain.Tick;
import com.github.guoyyit.conditionalorderenginejava.domain.TriggerDirection;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pure trigger-evaluation logic: selects the configured reference price from a
 * tick and compares it against the trigger price.
 */
@Component
public class TriggerEvaluator {

    /**
     * Returns the reference price of the tick according to the configured source,
     * or {@code null} when that quote is not available in this tick.
     */
    public BigDecimal referencePrice(Tick tick, PriceSource source) {
        return switch (source) {
            case LAST -> tick.getLast();
            case BID -> tick.getBid();
            case ASK -> tick.getAsk();
        };
    }

    /**
     * Returns true when the reference price meets the trigger condition
     * (greater-than-or-equal / less-than-or-equal).
     */
    public boolean isTriggered(ConditionalOrder order, BigDecimal referencePrice) {
        if (referencePrice == null || order.getTriggerPrice() == null || order.getDirection() == null) {
            return false;
        }
        return matches(order.getDirection(), referencePrice, order.getTriggerPrice());
    }

    /**
     * Whether the comparison direction is satisfied; exposed for tests/reuse.
     */
    public boolean matches(TriggerDirection direction, BigDecimal reference, BigDecimal trigger) {
        int cmp = reference.compareTo(trigger);
        return switch (direction) {
            case ABOVE -> cmp >= 0;
            case BELOW -> cmp <= 0;
        };
    }
}
