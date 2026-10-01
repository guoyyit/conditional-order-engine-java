package com.github.guoyyit.conditionalorderenginejava.tests;

import com.github.guoyyit.conditionalorderenginejava.config.EngineProperties;
import com.github.guoyyit.conditionalorderenginejava.engine.ExecutionEngine;
import com.github.guoyyit.conditionalorderenginejava.engine.TriggerEngine;
import com.github.guoyyit.conditionalorderenginejava.model.CompareDirection;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrderStatus;
import com.github.guoyyit.conditionalorderenginejava.model.ExecType;
import com.github.guoyyit.conditionalorderenginejava.model.MarketQuote;
import com.github.guoyyit.conditionalorderenginejava.model.OrderKind;
import com.github.guoyyit.conditionalorderenginejava.model.PriceSource;
import com.github.guoyyit.conditionalorderenginejava.model.Side;
import com.github.guoyyit.conditionalorderenginejava.model.TriggerRecord;
import com.github.guoyyit.conditionalorderenginejava.service.ConditionalOrderServiceImpl;
import com.github.guoyyit.conditionalorderenginejava.store.FileEventLogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TriggerActivationTest {

    @TempDir
    Path storeDir;

    private ConditionalOrderServiceImpl newService() {
        EngineProperties props = new EngineProperties();
        props.setStoreDir(storeDir.toString());
        ConditionalOrderServiceImpl service = new ConditionalOrderServiceImpl(
                new TriggerEngine(), new ExecutionEngine(),
                new FileEventLogStore(storeDir), props);
        service.recover();
        return service;
    }

    static ConditionalOrder stopLossSell(String id, String triggerPrice,
                                         ExecType execType, String limitPrice, String maxSlippage) {
        ConditionalOrder order = new ConditionalOrder();
        order.setOrderId(id);
        order.setAccountId("acc-1");
        order.setSymbol("BTCUSDT");
        order.setSide(Side.SELL);
        order.setKind(OrderKind.STOP_LOSS);
        order.setPriceSource(PriceSource.LAST_TRADE);
        order.setDirection(CompareDirection.LESS_EQUAL);
        order.setTriggerPrice(new BigDecimal(triggerPrice));
        order.setExecType(execType);
        order.setLimitPrice(limitPrice == null ? null : new BigDecimal(limitPrice));
        order.setMaxSlippage(maxSlippage == null ? null : new BigDecimal(maxSlippage));
        order.setQuantity(10);
        return order;
    }

    static MarketQuote quote(String last, String bid1, String ask1, long ts) {
        return new MarketQuote("BTCUSDT", new BigDecimal(last),
                new BigDecimal(bid1), new BigDecimal(ask1), ts);
    }

    @Test
    void activatesOnPriceCrossThroughWithFirstAvailablePrice() {
        ConditionalOrderServiceImpl service = newService();
        service.submit(stopLossSell("o1", "100", ExecType.LIMIT, "99.5", null));

        service.onMarketQuote(quote("101", "100.9", "101.1", 1000));
        assertEquals(ConditionalOrderStatus.PENDING, service.findById("o1").orElseThrow().getStatus());

        // 价格从 101 瞬时穿越触发价 100 到 99，按穿越后首个可用价格 99 判定激活
        service.onMarketQuote(quote("99", "99.8", "100.2", 2000));

        ConditionalOrder order = service.findById("o1").orElseThrow();
        assertEquals(ConditionalOrderStatus.FILLED, order.getStatus());

        List<TriggerRecord> history = service.findTriggerHistory("acc-1", "BTCUSDT");
        assertEquals(1, history.size());
        TriggerRecord record = history.get(0);
        assertEquals(2000, record.triggeredAt());
        // 判定价格为穿越后的 99，而非触发价 100
        assertEquals(new BigDecimal("99"), record.judgedPrice());
        assertEquals(new BigDecimal("-1"), record.judgeDeviation());
        // 限价 99.5 卖出，对手买一 99.8 更优，按 99.8 成交，不向不利方向追价
        assertEquals(new BigDecimal("99.8"), record.execPrice());
    }

    @Test
    void usesConfiguredPriceSourceForJudgement() {
        ConditionalOrderServiceImpl service = newService();
        ConditionalOrder order = stopLossSell("o2", "100", ExecType.LIMIT, "100.5", null);
        order.setSide(Side.BUY);
        order.setKind(OrderKind.TAKE_PROFIT);
        order.setDirection(CompareDirection.GREATER_EQUAL);
        order.setPriceSource(PriceSource.BID1);
        service.submit(order);

        // 最新成交价 99 未触及 100，但买一价 100.2 触及，按 BID1 判定应触发
        service.onMarketQuote(quote("99", "100.2", "100.4", 1000));

        assertEquals(ConditionalOrderStatus.FILLED, service.findById("o2").orElseThrow().getStatus());
        TriggerRecord record = service.findTriggerHistory("acc-1", "BTCUSDT").get(0);
        assertEquals(PriceSource.BID1, record.priceSource());
        assertEquals(new BigDecimal("100.2"), record.judgedPrice());
        // 限价买 100.5，卖一 100.4 更优，按 100.4 成交
        assertEquals(new BigDecimal("100.4"), record.execPrice());
    }

    @Test
    void limitOrderWaitsForLaterQuotesWhenPriceNotReachable() {
        ConditionalOrderServiceImpl service = newService();
        service.submit(stopLossSell("o3", "100", ExecType.LIMIT, "100", null));

        // 触发但买一 99 低于限价 100，不追价成交，保持已激活待成交
        service.onMarketQuote(quote("99", "99", "99.2", 1000));
        assertEquals(ConditionalOrderStatus.ACTIVATED, service.findById("o3").orElseThrow().getStatus());

        // 后续行情买一回到 100.5，按 100.5 成交
        service.onMarketQuote(quote("100.6", "100.5", "100.7", 2000));
        assertEquals(ConditionalOrderStatus.FILLED, service.findById("o3").orElseThrow().getStatus());
        assertEquals(new BigDecimal("100.5"),
                service.findTriggerHistory("acc-1", "BTCUSDT").get(0).execPrice());
    }

    @Test
    void marketOrderRejectedWhenSlippageExceedsLimit() {
        ConditionalOrderServiceImpl service = newService();
        ConditionalOrder buy = stopLossSell("o4", "100", ExecType.MARKET, null, "0.5");
        buy.setSide(Side.BUY);
        buy.setDirection(CompareDirection.GREATER_EQUAL);
        service.submit(buy);

        // 判定价 100.1，卖一 101，滑点 0.9 超过上限 0.5，拒绝执行
        service.onMarketQuote(quote("100.1", "100", "101", 1000));

        ConditionalOrder order = service.findById("o4").orElseThrow();
        assertEquals(ConditionalOrderStatus.SLIPPAGE_REJECTED, order.getStatus());
        TriggerRecord record = service.findTriggerHistory("acc-1", "BTCUSDT").get(0);
        assertEquals("SLIPPAGE_REJECTED", record.outcome());
        assertNull(record.execPrice());
        assertNotNull(record.detail());
    }

    @Test
    void marketOrderFillsWithinSlippageLimit() {
        ConditionalOrderServiceImpl service = newService();
        ConditionalOrder buy = stopLossSell("o5", "100", ExecType.MARKET, null, "0.5");
        buy.setSide(Side.BUY);
        buy.setDirection(CompareDirection.GREATER_EQUAL);
        service.submit(buy);

        // 判定价 100.1，卖一 100.4，滑点 0.3 在上限 0.5 内，成交
        service.onMarketQuote(quote("100.1", "100", "100.4", 1000));

        assertEquals(ConditionalOrderStatus.FILLED, service.findById("o5").orElseThrow().getStatus());
        assertEquals(new BigDecimal("100.4"),
                service.findTriggerHistory("acc-1", "BTCUSDT").get(0).execPrice());
    }

    @Test
    void pendingAndActivatedOrdersExpireByQuoteTime() {
        ConditionalOrderServiceImpl service = newService();
        ConditionalOrder pending = stopLossSell("e1", "90", ExecType.LIMIT, "89.5", null);
        pending.setExpireAt(5000);
        service.submit(pending);
        ConditionalOrder activated = stopLossSell("e2", "100", ExecType.LIMIT, "99", null);
        activated.setExpireAt(5000);
        service.submit(activated);

        // e2 触发但限价 99 未成交，挂在执行流程
        service.onMarketQuote(quote("99.5", "98", "99.6", 1000));
        assertEquals(ConditionalOrderStatus.PENDING, service.findById("e1").orElseThrow().getStatus());
        assertEquals(ConditionalOrderStatus.ACTIVATED, service.findById("e2").orElseThrow().getStatus());

        // 行情时间到达过期点，两单均过期
        service.onMarketQuote(quote("99.4", "99.3", "99.5", 5000));
        assertEquals(ConditionalOrderStatus.EXPIRED, service.findById("e1").orElseThrow().getStatus());
        assertEquals(ConditionalOrderStatus.EXPIRED, service.findById("e2").orElseThrow().getStatus());

        // 过期后不再触发或成交（89 已穿越 e1 触发价 90）
        service.onMarketQuote(quote("89", "99.8", "100.2", 6000));
        assertEquals(ConditionalOrderStatus.EXPIRED, service.findById("e1").orElseThrow().getStatus());
        assertEquals(ConditionalOrderStatus.EXPIRED, service.findById("e2").orElseThrow().getStatus());
    }
}
