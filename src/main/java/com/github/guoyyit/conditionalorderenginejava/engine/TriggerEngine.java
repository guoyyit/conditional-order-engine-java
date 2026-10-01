package com.github.guoyyit.conditionalorderenginejava.engine;

import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.CompareDirection;
import com.github.guoyyit.conditionalorderenginejava.model.MarketQuote;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 触发判定引擎：按配置的参考价格来源判断条件订单是否应被激活。
 */
@Component
public class TriggerEngine {

    /**
     * 判断给定行情下订单是否触发。价格瞬时穿越触发价时，
     * 以穿越后的首个可用价格（即当前行情价格）作为判定价格。
     *
     * @return 触发时的判定价格；未触发返回 null
     */
    public BigDecimal evaluate(ConditionalOrder order, MarketQuote quote) {
        if (!order.getSymbol().equals(quote.symbol())) {
            return null;
        }
        BigDecimal price = quote.priceOf(order.getPriceSource());
        if (price == null || order.getTriggerPrice() == null) {
            return null;
        }
        boolean hit = switch (order.getDirection()) {
            case GREATER_EQUAL -> price.compareTo(order.getTriggerPrice()) >= 0;
            case LESS_EQUAL -> price.compareTo(order.getTriggerPrice()) <= 0;
        };
        return hit ? price : null;
    }
}
