package com.github.guoyyit.conditionalorderenginejava.tests;

import com.github.guoyyit.conditionalorderenginejava.config.EngineProperties;
import com.github.guoyyit.conditionalorderenginejava.engine.ExecutionEngine;
import com.github.guoyyit.conditionalorderenginejava.engine.TriggerEngine;
import com.github.guoyyit.conditionalorderenginejava.model.CompareDirection;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrderStatus;
import com.github.guoyyit.conditionalorderenginejava.model.ExecType;
import com.github.guoyyit.conditionalorderenginejava.model.Side;
import com.github.guoyyit.conditionalorderenginejava.model.TriggerRecord;
import com.github.guoyyit.conditionalorderenginejava.service.ConditionalOrderServiceImpl;
import com.github.guoyyit.conditionalorderenginejava.store.FileEventLogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static com.github.guoyyit.conditionalorderenginejava.tests.TriggerActivationTest.quote;
import static com.github.guoyyit.conditionalorderenginejava.tests.TriggerActivationTest.stopLossSell;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 崩溃恢复：每条状态变更先写事件日志（fsync）再改内存，
 * 重启后重放日志恢复，不重复触发、不重复成交、不丢失已生效的修改与取消。
 */
class CrashRecoveryTest {

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

    @Test
    void recoversAllStatesAfterCrash() {
        ConditionalOrderServiceImpl before = newService();
        // A：未激活（触发价 90，崩溃前的行情不触及）
        before.submit(stopLossSell("A", "90", ExecType.LIMIT, "89.5", null));
        // B：激活但限价 100 未成交（崩溃前行情买一最高 99.8），挂在执行流程中
        before.submit(stopLossSell("B", "100", ExecType.LIMIT, "100", null));
        before.onMarketQuote(quote("99.5", "98", "99.6", 1000));
        assertEquals(ConditionalOrderStatus.ACTIVATED, before.findById("B").orElseThrow().getStatus());
        // C：激活并成交
        before.submit(stopLossSell("C", "100", ExecType.LIMIT, "99.5", null));
        before.onMarketQuote(quote("99", "99.8", "100.2", 2000));
        assertEquals(ConditionalOrderStatus.FILLED, before.findById("C").orElseThrow().getStatus());
        // D：提交后取消
        before.submit(stopLossSell("D", "100", ExecType.LIMIT, "99.5", null));
        assertTrue(before.cancel("D"));
        // E：提交后修改参数
        before.submit(stopLossSell("E", "100", ExecType.LIMIT, "99.5", null));
        before.modify("E", stopLossSell(null, "95", ExecType.LIMIT, "94.5", null));
        // 模拟崩溃：不 close，直接新建实例从日志恢复（append 已逐条 fsync）
        ConditionalOrderServiceImpl after = newService();
        // 未激活状态恢复
        assertEquals(ConditionalOrderStatus.PENDING, after.findById("A").orElseThrow().getStatus());
        // 已激活状态恢复，触发记录保留
        assertEquals(ConditionalOrderStatus.ACTIVATED, after.findById("B").orElseThrow().getStatus());
        TriggerRecord bRecord = after.findTriggerHistory("acc-1", "BTCUSDT").stream()
                .filter(r -> r.orderId().equals("B")).findFirst().orElseThrow();
        assertEquals(1000, bRecord.triggeredAt());
        assertEquals(new BigDecimal("99.5"), bRecord.judgedPrice());
        // 已成交状态恢复
        assertEquals(ConditionalOrderStatus.FILLED, after.findById("C").orElseThrow().getStatus());
        // 崩溃前已生效的取消不丢失
        assertEquals(ConditionalOrderStatus.CANCELLED, after.findById("D").orElseThrow().getStatus());
        // 崩溃前已生效的修改不丢失
        ConditionalOrder e = after.findById("E").orElseThrow();
        assertEquals(ConditionalOrderStatus.PENDING, e.getStatus());
        assertEquals(new BigDecimal("95"), e.getTriggerPrice());
        assertEquals(1, e.getVersion());
        // 恢复后继续驱动：A 正常触发成交
        after.onMarketQuote(quote("89", "89.8", "90.2", 3000));
        assertEquals(ConditionalOrderStatus.FILLED, after.findById("A").orElseThrow().getStatus());
        // B 在恢复后遇到满足限价的行情（买一 100.2 >= 限价 100）才成交，触发时间不重复刷新
        after.onMarketQuote(quote("100.5", "100.2", "100.6", 4000));
        assertEquals(ConditionalOrderStatus.FILLED, after.findById("B").orElseThrow().getStatus());
        TriggerRecord bFilled = after.findTriggerHistory("acc-1", "BTCUSDT").stream()
                .filter(r -> r.orderId().equals("B")).findFirst().orElseThrow();
        assertEquals(1000, bFilled.triggeredAt());
        assertEquals(new BigDecimal("100.2"), bFilled.execPrice());
        // C 已成交，注入行情不重复成交（96 不触及 E 的触发价 95）
        after.onMarketQuote(quote("96", "95.9", "96.1", 5000));
        TriggerRecord cRecord = after.findTriggerHistory("acc-1", "BTCUSDT").stream()
                .filter(r -> r.orderId().equals("C")).findFirst().orElseThrow();
        assertEquals(2000, cRecord.triggeredAt());
        assertEquals(new BigDecimal("99.8"), cRecord.execPrice());
        // D 已取消，注入穿越触发价的行情也不会触发
        assertEquals(ConditionalOrderStatus.CANCELLED, after.findById("D").orElseThrow().getStatus());
        List<TriggerRecord> dRecords = after.findTriggerHistory("acc-1", "BTCUSDT").stream()
                .filter(r -> r.orderId().equals("D")).toList();
        assertTrue(dRecords.isEmpty());
        // 每个订单在触发历史中至多一条记录，无重复触发
        List<TriggerRecord> all = after.findTriggerHistory("acc-1", "BTCUSDT");
        assertEquals(all.size(), all.stream().map(TriggerRecord::orderId).distinct().count());
    }

    @Test
    void recoversSlippageRejectedState() {
        ConditionalOrderServiceImpl before = newService();
        ConditionalOrder buy = stopLossSell("S1", "100", ExecType.MARKET, null, "0.5");
        buy.setSide(Side.BUY);
        buy.setDirection(CompareDirection.GREATER_EQUAL);
        before.submit(buy);
        before.onMarketQuote(quote("100.1", "100", "101", 1000));
        assertEquals(ConditionalOrderStatus.SLIPPAGE_REJECTED,
                before.findById("S1").orElseThrow().getStatus());
        ConditionalOrderServiceImpl after = newService();
        assertEquals(ConditionalOrderStatus.SLIPPAGE_REJECTED,
                after.findById("S1").orElseThrow().getStatus());
        // 恢复后行情回落也不重新执行，终态不可逆
        after.onMarketQuote(quote("100.1", "100", "100.2", 2000));
        assertEquals(ConditionalOrderStatus.SLIPPAGE_REJECTED,
                after.findById("S1").orElseThrow().getStatus());
    }
}
