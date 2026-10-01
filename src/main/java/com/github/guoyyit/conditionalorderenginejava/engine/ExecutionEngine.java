package com.github.guoyyit.conditionalorderenginejava.engine;

import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.MarketQuote;
import com.github.guoyyit.conditionalorderenginejava.model.Trade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 执行引擎：激活后的订单按限价或市价（受滑点上限约束）执行。
 * 成交价不得劣于订单约束，不向不利方向追价。
 */
@Component
public class ExecutionEngine {

    /**
     * @param judgedPrice 触发判定价格，作为市价单滑点基准；直接下单场景可为 null
     */
    public ExecutionResult execute(ConditionalOrder order, MarketQuote quote, BigDecimal judgedPrice) {
        return switch (order.getExecType()) {
            case LIMIT -> executeLimit(order, quote);
            case MARKET -> executeMarket(order, quote, judgedPrice);
        };
    }

    private ExecutionResult executeLimit(ConditionalOrder order, MarketQuote quote) {
        BigDecimal limit = order.getLimitPrice();
        BigDecimal fillPrice = switch (order.getSide()) {
            case BUY -> (quote.ask1() != null && limit != null
                    && quote.ask1().compareTo(limit) <= 0) ? quote.ask1() : null;
            case SELL -> (quote.bid1() != null && limit != null
                    && quote.bid1().compareTo(limit) >= 0) ? quote.bid1() : null;
        };
        if (fillPrice == null) {
            return ExecutionResult.pending();
        }
        return ExecutionResult.filled(newTrade(order, fillPrice, quote.timestamp()));
    }

    private ExecutionResult executeMarket(ConditionalOrder order, MarketQuote quote, BigDecimal judgedPrice) {
        BigDecimal fillPrice = switch (order.getSide()) {
            case BUY -> quote.ask1();
            case SELL -> quote.bid1();
        };
        if (fillPrice == null) {
            return ExecutionResult.pending();
        }
        BigDecimal maxSlippage = order.getMaxSlippage();
        if (maxSlippage != null && judgedPrice != null) {
            BigDecimal slippage = switch (order.getSide()) {
                case BUY -> fillPrice.subtract(judgedPrice);
                case SELL -> judgedPrice.subtract(fillPrice);
            };
            if (slippage.compareTo(maxSlippage) > 0) {
                return ExecutionResult.slippageRejected(
                        "slippage " + slippage.toPlainString()
                                + " exceeds max " + maxSlippage.toPlainString());
            }
        }
        return ExecutionResult.filled(newTrade(order, fillPrice, quote.timestamp()));
    }

    private Trade newTrade(ConditionalOrder order, BigDecimal price, long timestamp) {
        return new Trade(UUID.randomUUID().toString(), order.getOrderId(), order.getAccountId(),
                order.getSymbol(), order.getSide(), price, order.getQuantity(), timestamp);
    }
}
