package com.github.guoyyit.conditionalorderenginejava.tests;

import com.github.guoyyit.conditionalorderenginejava.config.EngineProperties;
import com.github.guoyyit.conditionalorderenginejava.engine.ExecutionEngine;
import com.github.guoyyit.conditionalorderenginejava.engine.TriggerEngine;
import com.github.guoyyit.conditionalorderenginejava.model.CompareDirection;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrderStatus;
import com.github.guoyyit.conditionalorderenginejava.model.ExecType;
import com.github.guoyyit.conditionalorderenginejava.model.TriggerRecord;
import com.github.guoyyit.conditionalorderenginejava.service.ConditionalOrderServiceImpl;
import com.github.guoyyit.conditionalorderenginejava.store.FileEventLogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.guoyyit.conditionalorderenginejava.tests.TriggerActivationTest.quote;
import static com.github.guoyyit.conditionalorderenginejava.tests.TriggerActivationTest.stopLossSell;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 修改/取消与触发并发发生时，结果必须确定：
 * 要么按旧参数完成激活，要么按新参数生效或取消，不得出现部分生效的中间状态。
 */
class ConcurrencyTest {

    @TempDir
    Path storeDir;

    private ConditionalOrderServiceImpl newService() {
        return newService(storeDir);
    }

    private ConditionalOrderServiceImpl newService(Path dir) {
        EngineProperties props = new EngineProperties();
        props.setStoreDir(dir.toString());
        ConditionalOrderServiceImpl service = new ConditionalOrderServiceImpl(
                new TriggerEngine(), new ExecutionEngine(),
                new FileEventLogStore(dir), props);
        service.recover();
        return service;
    }

    @Test
    void modifyTakesEffectAtomicallyBeforeTrigger() {
        ConditionalOrderServiceImpl service = newService();
        service.submit(stopLossSell("m1", "100", ExecType.LIMIT, "99.5", null));

        ConditionalOrder newParams = stopLossSell(null, "90", ExecType.LIMIT, "89.5", null);
        service.modify("m1", newParams);

        // 95 触及旧触发价 100 但未触及新触发价 90，按新参数不触发
        service.onMarketQuote(quote("95", "94.9", "95.1", 1000));
        assertEquals(ConditionalOrderStatus.PENDING, service.findById("m1").orElseThrow().getStatus());

        // 89 触及新触发价 90，按新参数激活
        service.onMarketQuote(quote("89", "89.6", "89.8", 2000));
        assertEquals(ConditionalOrderStatus.FILLED, service.findById("m1").orElseThrow().getStatus());
        TriggerRecord record = service.findTriggerHistory("acc-1", "BTCUSDT").get(0);
        assertEquals(new BigDecimal("90"), record.triggerPrice());
        assertEquals(new BigDecimal("89"), record.judgedPrice());
    }

    @Test
    void modifyRejectedAfterActivation() {
        ConditionalOrderServiceImpl service = newService();
        service.submit(stopLossSell("m2", "100", ExecType.LIMIT, "99.5", null));
        service.onMarketQuote(quote("99", "99.8", "100.2", 1000));
        assertEquals(ConditionalOrderStatus.FILLED, service.findById("m2").orElseThrow().getStatus());

        assertThrows(IllegalStateException.class,
                () -> service.modify("m2", stopLossSell(null, "90", ExecType.LIMIT, "89.5", null)));
    }

    @Test
    void concurrentCancelAndTriggerIsDeterministic() throws Exception {
        for (int round = 0; round < 50; round++) {
            ConditionalOrderServiceImpl service = newService(storeDir.resolve("cancel-" + round));
            String orderId = "c" + round;
            service.submit(stopLossSell(orderId, "100", ExecType.LIMIT, "99.5", null));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> cancelResult = pool.submit(() -> {
                ready.countDown();
                start.await();
                return service.cancel(orderId);
            });
            Future<?> triggerResult = pool.submit(() -> {
                ready.countDown();
                start.await();
                service.onMarketQuote(quote("99", "99.8", "100.2", 1000));
                return null;
            });
            ready.await();
            start.countDown();
            boolean cancelled = cancelResult.get(10, TimeUnit.SECONDS);
            triggerResult.get(10, TimeUnit.SECONDS);
            pool.shutdown();

            ConditionalOrder order = service.findById(orderId).orElseThrow();
            List<TriggerRecord> history = service.findTriggerHistory("acc-1", "BTCUSDT");
            if (cancelled) {
                // 取消先生效：终态为已取消，绝不允许出现触发记录
                assertEquals(ConditionalOrderStatus.CANCELLED, order.getStatus());
                assertTrue(history.isEmpty());
            } else {
                // 触发先生效：订单已激活并成交，取消返回 false，状态与记录一致
                assertEquals(ConditionalOrderStatus.FILLED, order.getStatus());
                assertEquals(1, history.size());
                assertEquals("FILLED", history.get(0).outcome());
            }
        }
    }

    @Test
    void concurrentModifyAndTriggerNeverPartiallyApplied() throws Exception {
        for (int round = 0; round < 50; round++) {
            ConditionalOrderServiceImpl service = newService(storeDir.resolve("modify-" + round));
            String orderId = "x" + round;
            service.submit(stopLossSell(orderId, "100", ExecType.LIMIT, "99.5", null));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<?> modifier = pool.submit(() -> {
                ready.countDown();
                start.await();
                for (int i = 0; i < 20; i++) {
                    try {
                        service.modify(orderId,
                                stopLossSell(null, "9" + (i % 10), ExecType.LIMIT, "88.5", null));
                    } catch (IllegalStateException ignored) {
                        // 已激活后修改被拒绝，属预期
                        return null;
                    }
                }
                return null;
            });
            Future<?> trigger = pool.submit(() -> {
                ready.countDown();
                start.await();
                for (int i = 0; i < 20; i++) {
                    service.onMarketQuote(quote("85", "89.6", "89.8", 1000 + i));
                }
                return null;
            });
            ready.await();
            start.countDown();
            modifier.get(10, TimeUnit.SECONDS);
            trigger.get(10, TimeUnit.SECONDS);
            pool.shutdown();

            ConditionalOrder order = service.findById(orderId).orElseThrow();
            List<TriggerRecord> history = service.findTriggerHistory("acc-1", "BTCUSDT");
            if (order.getStatus() == ConditionalOrderStatus.PENDING) {
                // 未触发：必须仍是 PENDING 且无任何触发记录
                assertTrue(history.isEmpty());
            } else {
                // 已激活（限价未成交挂单）或已成交：激活按某一版本参数完整生效，
                // 触发记录中的触发价必须等于订单当前触发价
                assertTrue(order.getStatus() == ConditionalOrderStatus.ACTIVATED
                        || order.getStatus() == ConditionalOrderStatus.FILLED);
                assertEquals(1, history.size());
                assertEquals(order.getTriggerPrice(), history.get(0).triggerPrice());
                // 判定价格满足比较方向（LESS_EQUAL：判定价 <= 触发价）
                assertTrue(history.get(0).judgedPrice().compareTo(history.get(0).triggerPrice()) <= 0);
            }
        }
    }
}
