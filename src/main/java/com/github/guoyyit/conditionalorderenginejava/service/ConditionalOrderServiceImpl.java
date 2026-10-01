package com.github.guoyyit.conditionalorderenginejava.service;

import com.github.guoyyit.conditionalorderenginejava.config.EngineProperties;
import com.github.guoyyit.conditionalorderenginejava.engine.ExecutionEngine;
import com.github.guoyyit.conditionalorderenginejava.engine.ExecutionResult;
import com.github.guoyyit.conditionalorderenginejava.engine.TriggerEngine;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrderStatus;
import com.github.guoyyit.conditionalorderenginejava.model.ExecType;
import com.github.guoyyit.conditionalorderenginejava.model.MarketQuote;
import com.github.guoyyit.conditionalorderenginejava.model.Trade;
import com.github.guoyyit.conditionalorderenginejava.model.TriggerRecord;
import com.github.guoyyit.conditionalorderenginejava.store.EventLogStore;
import com.github.guoyyit.conditionalorderenginejava.store.OrderEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 条件订单服务实现。
 *
 * 并发语义：所有状态变更在同一把锁内串行执行，且先写事件日志（fsync）再改内存，
 * 因此修改/取消与触发并发时结果确定——要么触发先完成（按旧参数激活），
 * 要么修改/取消先完成（触发时按新参数判定或订单已取消），无中间态。
 *
 * 崩溃恢复：启动时重放事件日志重建内存状态；ACTIVATED 订单恢复后继续执行流程，
 * 不重复触发；FILLED 为终态，不重复成交。
 */
@Service
public class ConditionalOrderServiceImpl implements ConditionalOrderService {

    private final TriggerEngine triggerEngine;
    private final ExecutionEngine executionEngine;
    private final EventLogStore eventLog;
    private final EngineProperties properties;

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, ConditionalOrder> orders = new LinkedHashMap<>();
    private final Map<String, TriggerRecord> triggerRecords = new LinkedHashMap<>();

    public ConditionalOrderServiceImpl(TriggerEngine triggerEngine,
                                       ExecutionEngine executionEngine,
                                       EventLogStore eventLog,
                                       EngineProperties properties) {
        this.triggerEngine = triggerEngine;
        this.executionEngine = executionEngine;
        this.eventLog = eventLog;
        this.properties = properties;
    }

    @PostConstruct
    public void recover() {
        for (OrderEvent event : eventLog.loadAll()) {
            apply(event);
        }
    }

    @PreDestroy
    public void shutdown() {
        eventLog.close();
    }

    private void apply(OrderEvent event) {
        ConditionalOrder order = event.order();
        switch (event.type()) {
            case SUBMITTED, MODIFIED -> orders.put(order.getOrderId(), order);
            case CANCELLED, EXPIRED, SLIPPAGE_REJECTED -> orders.put(order.getOrderId(), order);
            case ACTIVATED -> {
                orders.put(order.getOrderId(), order);
                triggerRecords.put(order.getOrderId(), event.triggerRecord());
            }
            case FILLED -> {
                orders.put(order.getOrderId(), order);
                triggerRecords.put(order.getOrderId(), event.triggerRecord());
            }
        }
    }

    @Override
    public ConditionalOrder submit(ConditionalOrder order) {
        lock.lock();
        try {
            validate(order);
            if (order.getOrderId() == null || order.getOrderId().isBlank()) {
                order.setOrderId(UUID.randomUUID().toString());
            }
            if (orders.containsKey(order.getOrderId())) {
                throw new IllegalArgumentException("duplicate orderId: " + order.getOrderId());
            }
            order.setCreatedAt(System.currentTimeMillis());
            order.setVersion(0);
            order.setStatus(ConditionalOrderStatus.PENDING);
            if (order.getExecType() == ExecType.MARKET && order.getMaxSlippage() == null) {
                order.setMaxSlippage(properties.getDefaultMaxSlippage());
            }
            eventLog.append(OrderEvent.of(OrderEvent.EventType.SUBMITTED, order));
            orders.put(order.getOrderId(), order);
            return order;
        } finally {
            lock.unlock();
        }
    }

    private void validate(ConditionalOrder order) {
        if (order.getAccountId() == null || order.getSymbol() == null
                || order.getSide() == null || order.getKind() == null
                || order.getPriceSource() == null || order.getDirection() == null
                || order.getTriggerPrice() == null || order.getExecType() == null
                || order.getQuantity() <= 0) {
            throw new IllegalArgumentException("missing required order fields");
        }
        if (order.getExecType() == ExecType.LIMIT && order.getLimitPrice() == null) {
            throw new IllegalArgumentException("limitPrice required for LIMIT exec type");
        }
    }

    @Override
    public ConditionalOrder modify(String orderId, ConditionalOrder newParams) {
        lock.lock();
        try {
            ConditionalOrder current = orders.get(orderId);
            if (current == null) {
                throw new IllegalArgumentException("order not found: " + orderId);
            }
            if (current.getStatus() != ConditionalOrderStatus.PENDING) {
                throw new IllegalStateException(
                        "order " + orderId + " is " + current.getStatus() + ", cannot modify");
            }
            newParams.setOrderId(orderId);
            newParams.setAccountId(current.getAccountId());
            newParams.setCreatedAt(current.getCreatedAt());
            newParams.setVersion(current.getVersion() + 1);
            newParams.setStatus(ConditionalOrderStatus.PENDING);
            validate(newParams);
            if (newParams.getExecType() == ExecType.MARKET && newParams.getMaxSlippage() == null) {
                newParams.setMaxSlippage(properties.getDefaultMaxSlippage());
            }
            eventLog.append(OrderEvent.of(OrderEvent.EventType.MODIFIED, newParams));
            orders.put(orderId, newParams);
            return newParams;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean cancel(String orderId) {
        lock.lock();
        try {
            ConditionalOrder current = orders.get(orderId);
            if (current == null) {
                return false;
            }
            if (current.getStatus() != ConditionalOrderStatus.PENDING
                    && current.getStatus() != ConditionalOrderStatus.ACTIVATED) {
                return false;
            }
            current.setStatus(ConditionalOrderStatus.CANCELLED);
            eventLog.append(OrderEvent.of(OrderEvent.EventType.CANCELLED, current));
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onMarketQuote(MarketQuote quote) {
        lock.lock();
        try {
            for (ConditionalOrder order : new ArrayList<>(orders.values())) {
                if (!order.getSymbol().equals(quote.symbol())) {
                    continue;
                }
                if (order.getExpireAt() > 0 && quote.timestamp() >= order.getExpireAt()
                        && (order.getStatus() == ConditionalOrderStatus.PENDING
                        || order.getStatus() == ConditionalOrderStatus.ACTIVATED)) {
                    order.setStatus(ConditionalOrderStatus.EXPIRED);
                    eventLog.append(OrderEvent.of(OrderEvent.EventType.EXPIRED, order));
                    continue;
                }
                switch (order.getStatus()) {
                    case PENDING -> tryTrigger(order, quote);
                    case ACTIVATED -> tryExecute(order, quote, judgedPriceOf(order));
                    default -> {
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private BigDecimal judgedPriceOf(ConditionalOrder order) {
        TriggerRecord record = triggerRecords.get(order.getOrderId());
        return record == null ? null : record.judgedPrice();
    }

    private void tryTrigger(ConditionalOrder order, MarketQuote quote) {
        BigDecimal judgedPrice = triggerEngine.evaluate(order, quote);
        if (judgedPrice == null) {
            return;
        }
        order.setStatus(ConditionalOrderStatus.ACTIVATED);
        TriggerRecord record = new TriggerRecord(
                order.getOrderId(), order.getAccountId(), order.getSymbol(),
                quote.timestamp(), order.getPriceSource(), judgedPrice,
                order.getTriggerPrice(), null, "ACTIVATED", null);
        eventLog.append(OrderEvent.activated(order, record));
        triggerRecords.put(order.getOrderId(), record);
        tryExecute(order, quote, judgedPrice);
    }

    private void tryExecute(ConditionalOrder order, MarketQuote quote, BigDecimal judgedPrice) {
        ExecutionResult result = executionEngine.execute(order, quote, judgedPrice);
        TriggerRecord prev = triggerRecords.get(order.getOrderId());
        long triggeredAt = prev != null ? prev.triggeredAt() : quote.timestamp();
        if (result.filled()) {
            Trade trade = result.trade();
            order.setStatus(ConditionalOrderStatus.FILLED);
            TriggerRecord record = new TriggerRecord(
                    order.getOrderId(), order.getAccountId(), order.getSymbol(),
                    triggeredAt, order.getPriceSource(), judgedPrice,
                    order.getTriggerPrice(), trade.price(), "FILLED", null);
            eventLog.append(OrderEvent.filled(order, trade, record));
            triggerRecords.put(order.getOrderId(), record);
        } else if (result.isTerminalReject()) {
            order.setStatus(ConditionalOrderStatus.SLIPPAGE_REJECTED);
            TriggerRecord record = new TriggerRecord(
                    order.getOrderId(), order.getAccountId(), order.getSymbol(),
                    triggeredAt, order.getPriceSource(), judgedPrice,
                    order.getTriggerPrice(), null, "SLIPPAGE_REJECTED", result.rejectReason());
            eventLog.append(new OrderEvent(OrderEvent.EventType.SLIPPAGE_REJECTED, order,
                    null, record, System.currentTimeMillis()));
            triggerRecords.put(order.getOrderId(), record);
        }
    }

    @Override
    public Optional<ConditionalOrder> findById(String orderId) {
        lock.lock();
        try {
            return Optional.ofNullable(orders.get(orderId));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ConditionalOrder> findByAccount(String accountId) {
        lock.lock();
        try {
            return orders.values().stream()
                    .filter(o -> o.getAccountId().equals(accountId))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ConditionalOrder> findByAccountAndSymbol(String accountId, String symbol) {
        lock.lock();
        try {
            return orders.values().stream()
                    .filter(o -> o.getAccountId().equals(accountId) && o.getSymbol().equals(symbol))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<TriggerRecord> findTriggerHistory(String accountId, String symbol) {
        lock.lock();
        try {
            return triggerRecords.values().stream()
                    .filter(r -> r.accountId().equals(accountId))
                    .filter(r -> symbol == null || r.symbol().equals(symbol))
                    .toList();
        } finally {
            lock.unlock();
        }
    }
}
