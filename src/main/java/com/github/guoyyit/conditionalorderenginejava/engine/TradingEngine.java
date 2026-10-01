package com.github.guoyyit.conditionalorderenginejava.engine;

import com.github.guoyyit.conditionalorderenginejava.config.EngineProperties;
import com.github.guoyyit.conditionalorderenginejava.domain.ConditionalStatus;
import com.github.guoyyit.conditionalorderenginejava.domain.ExecutionType;
import com.github.guoyyit.conditionalorderenginejava.domain.OrderStatus;
import com.github.guoyyit.conditionalorderenginejava.domain.Tick;
import com.github.guoyyit.conditionalorderenginejava.dto.CancelRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.CreateConditionalRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.CreateOrderRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.ModifyConditionalRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.TriggerHistoryView;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.OrdinaryOrder;
import com.github.guoyyit.conditionalorderenginejava.store.EventType;
import com.github.guoyyit.conditionalorderenginejava.store.EventStore;
import com.github.guoyyit.conditionalorderenginejava.store.InMemoryOrderStore;
import com.github.guoyyit.conditionalorderenginejava.store.LoggedEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Central, lock-serialized facade of the trading engine. Every mutating
 * operation (including tick injection) takes the same lock, which makes the
 * outcome of trigger vs. modify/cancel concurrency deterministic and atomic.
 */
@Component
public class TradingEngine {

    private final InMemoryOrderStore orderStore;
    private final EventStore eventStore;
    private final EngineProperties properties;
    private final TriggerEvaluator evaluator;
    private final OrderExecutor executor;

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, AtomicLong> symbolTickSeq = new ConcurrentHashMap<>();

    public TradingEngine(InMemoryOrderStore orderStore,
                         EventStore eventStore,
                         EngineProperties properties,
                         TriggerEvaluator evaluator,
                         OrderExecutor executor) {
        this.orderStore = orderStore;
        this.eventStore = eventStore;
        this.properties = properties;
        this.evaluator = evaluator;
        this.executor = executor;
        for (LoggedEvent event : orderStore.allEvents()) {
            if (event.getTickSeq() > 0) {
                symbolTickSeq.computeIfAbsent(event.getSymbol(), s -> new AtomicLong())
                        .accumulateAndGet(event.getTickSeq(), Math::max);
            }
        }
    }

    public ConditionalOrder createConditional(CreateConditionalRequest request) {
        requireText(request.getAccountId(), "accountId");
        requireText(request.getSymbol(), "symbol");
        requireNonNull(request.getSide(), "side");
        requireNonNull(request.getType(), "type");
        requireNonNull(request.getDirection(), "direction");
        requirePositive(request.getTriggerPrice(), "triggerPrice");
        requirePositive(request.getQuantity(), "quantity");
        ExecutionType executionType = request.getExecutionType() == null
                ? ExecutionType.MARKET
                : request.getExecutionType();
        if (executionType == ExecutionType.LIMIT && !isPositive(request.getLimitPrice())) {
            throw new ValidationException("limitPrice is required for LIMIT execution");
        }
        long maxSlippageBps = request.getMaxSlippageBps() == null
                ? properties.getDefaultMaxSlippageBps()
                : request.getMaxSlippageBps();
        if (maxSlippageBps < 0) {
            throw new ValidationException("maxSlippageBps must be non-negative");
        }
        long now = System.currentTimeMillis();
        lock.lock();
        try {
            ConditionalOrder order = new ConditionalOrder();
            order.setId("C-" + UUID.randomUUID());
            order.setVersion(1);
            order.setAccountId(request.getAccountId());
            order.setSymbol(request.getSymbol());
            order.setSide(request.getSide());
            order.setType(request.getType());
            order.setDirection(request.getDirection());
            order.setTriggerPrice(request.getTriggerPrice());
            order.setPriceSource(request.getPriceSource() == null
                    ? properties.getDefaultPriceSource()
                    : request.getPriceSource());
            order.setExecutionType(executionType);
            order.setLimitPrice(request.getLimitPrice());
            order.setQuantity(request.getQuantity());
            order.setMaxSlippageBps(maxSlippageBps);
            order.setExpireAt(request.getExpireAt());
            order.setStatus(ConditionalStatus.PENDING);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            LoggedEvent event = baseEvent(EventType.CONDITIONAL_CREATED, now);
            event.setConditionalOrderId(order.getId());
            event.setAccountId(order.getAccountId());
            event.setSymbol(order.getSymbol());
            event.setVersion(order.getVersion());
            event.setSide(order.getSide().name());
            event.setConditionalType(order.getType().name());
            event.setDirection(order.getDirection().name());
            event.setPriceSource(order.getPriceSource().name());
            event.setExecutionType(order.getExecutionType().name());
            event.setTriggerPrice(str(order.getTriggerPrice()));
            event.setLimitPrice(str(order.getLimitPrice()));
            event.setQuantity(order.getQuantity());
            event.setMaxSlippageBps(order.getMaxSlippageBps());
            event.setExpireAt(order.getExpireAt());
            persist(event);
            orderStore.putConditional(order);
            return order;
        } finally {
            lock.unlock();
        }
    }

    public ConditionalOrder modifyConditional(String id, ModifyConditionalRequest request) {
        ConditionalOrder order = pendingConditional(id);
        if (request.getExpectedVersion() != null && request.getExpectedVersion() != order.getVersion()) {
            throw new ConflictException("conditional order version conflict: expected "
                    + request.getExpectedVersion() + ", actual " + order.getVersion());
        }
        if (request.getTriggerPrice() != null && request.getTriggerPrice().signum() <= 0) {
            throw new ValidationException("triggerPrice must be positive");
        }
        if (request.getQuantity() != null && request.getQuantity() <= 0) {
            throw new ValidationException("quantity must be positive");
        }
        if (request.getMaxSlippageBps() != null && request.getMaxSlippageBps() < 0) {
            throw new ValidationException("maxSlippageBps must be non-negative");
        }
        if (request.getExecutionType() == ExecutionType.LIMIT
                && request.getLimitPrice() != null && request.getLimitPrice().signum() <= 0) {
            throw new ValidationException("limitPrice must be positive");
        }
        long now = System.currentTimeMillis();
        lock.lock();
        try {
            order = pendingConditional(id);
            if (request.getExpectedVersion() != null && request.getExpectedVersion() != order.getVersion()) {
                throw new ConflictException("conditional order version conflict: expected "
                        + request.getExpectedVersion() + ", actual " + order.getVersion());
            }
            LoggedEvent event = baseEvent(EventType.CONDITIONAL_MODIFIED, now);
            event.setConditionalOrderId(order.getId());
            event.setAccountId(order.getAccountId());
            event.setSymbol(order.getSymbol());
            event.setVersion(order.getVersion() + 1);
            if (request.getDirection() != null) {
                order.setDirection(request.getDirection());
                event.setDirection(order.getDirection().name());
            }
            if (request.getTriggerPrice() != null) {
                order.setTriggerPrice(request.getTriggerPrice());
                event.setTriggerPrice(str(order.getTriggerPrice()));
            }
            if (request.getPriceSource() != null) {
                order.setPriceSource(request.getPriceSource());
                event.setPriceSource(order.getPriceSource().name());
            }
            if (request.getExecutionType() != null) {
                order.setExecutionType(request.getExecutionType());
                event.setExecutionType(order.getExecutionType().name());
            }
            if (request.getLimitPrice() != null) {
                order.setLimitPrice(request.getLimitPrice());
                event.setLimitPrice(str(order.getLimitPrice()));
            }
            if (request.getQuantity() != null) {
                order.setQuantity(request.getQuantity());
                event.setQuantity(order.getQuantity());
            }
            if (request.getMaxSlippageBps() != null) {
                order.setMaxSlippageBps(request.getMaxSlippageBps());
                event.setMaxSlippageBps(order.getMaxSlippageBps());
            }
            order.setExpireAt(request.getExpireAt());
            event.setExpireAt(order.getExpireAt());
            order.setVersion(order.getVersion() + 1);
            order.setUpdatedAt(now);
            persist(event);
            return order;
        } finally {
            lock.unlock();
        }
    }

    public ConditionalOrder cancelConditional(String id, CancelRequest request) {
        long now = System.currentTimeMillis();
        lock.lock();
        try {
            ConditionalOrder order = orderStore.getConditional(id);
            if (order == null) {
                throw new NotFoundException("conditional order not found: " + id);
            }
            if (request != null && request.getAccountId() != null
                    && !request.getAccountId().equals(order.getAccountId())) {
                throw new NotFoundException("conditional order not found: " + id);
            }
            if (request != null && request.getExpectedVersion() != null
                    && request.getExpectedVersion() != order.getVersion()) {
                throw new ConflictException("conditional order version conflict: expected "
                        + request.getExpectedVersion() + ", actual " + order.getVersion());
            }
            if (order.getStatus() == ConditionalStatus.CANCELLED
                    || order.getStatus() == ConditionalStatus.EXPIRED) {
                throw new ConflictException("conditional order already " + order.getStatus());
            }
            if (order.getStatus() != ConditionalStatus.PENDING) {
                throw new ConflictException("conditional order already activated");
            }
            order.setStatus(ConditionalStatus.CANCELLED);
            order.setUpdatedAt(now);
            LoggedEvent event = baseEvent(EventType.CONDITIONAL_CANCELLED, now);
            event.setConditionalOrderId(order.getId());
            event.setAccountId(order.getAccountId());
            event.setSymbol(order.getSymbol());
            event.setVersion(order.getVersion());
            persist(event);
            return order;
        } finally {
            lock.unlock();
        }
    }

    public OrdinaryOrder placeOrder(CreateOrderRequest request) {
        requireText(request.getAccountId(), "accountId");
        requireText(request.getSymbol(), "symbol");
        requireNonNull(request.getSide(), "side");
        requireNonNull(request.getExecutionType(), "executionType");
        requirePositive(request.getQuantity(), "quantity");
        if (request.getExecutionType() == ExecutionType.LIMIT && !isPositive(request.getLimitPrice())) {
            throw new ValidationException("limitPrice is required for LIMIT execution");
        }
        long maxSlippageBps = request.getMaxSlippageBps() == null
                ? properties.getDefaultMaxSlippageBps()
                : request.getMaxSlippageBps();
        if (maxSlippageBps < 0) {
            throw new ValidationException("maxSlippageBps must be non-negative");
        }
        long now = System.currentTimeMillis();
        lock.lock();
        try {
            OrdinaryOrder order = new OrdinaryOrder();
            order.setId("O-" + UUID.randomUUID());
            order.setAccountId(request.getAccountId());
            order.setSymbol(request.getSymbol());
            order.setSide(request.getSide());
            order.setExecutionType(request.getExecutionType());
            order.setLimitPrice(request.getLimitPrice());
            order.setQuantity(request.getQuantity());
            order.setMaxSlippageBps(maxSlippageBps);
            order.setExpireAt(request.getExpireAt());
            order.setStatus(OrderStatus.NEW);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            persist(orderPlacedEvent(order, now));
            orderStore.putOrder(order);
            return order;
        } finally {
            lock.unlock();
        }
    }

    public OrdinaryOrder cancelOrder(String id, CancelRequest request) {
        long now = System.currentTimeMillis();
        lock.lock();
        try {
            OrdinaryOrder order = orderStore.getOrder(id);
            if (order == null) {
                throw new NotFoundException("order not found: " + id);
            }
            if (request != null && request.getAccountId() != null
                    && !request.getAccountId().equals(order.getAccountId())) {
                throw new NotFoundException("order not found: " + id);
            }
            if (order.getStatus() != OrderStatus.NEW) {
                throw new ConflictException("order already " + order.getStatus());
            }
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(now);
            LoggedEvent event = baseEvent(EventType.ORDER_CANCELLED, now);
            event.setOrderId(order.getId());
            event.setConditionalOrderId(order.getConditionalOrderId());
            event.setAccountId(order.getAccountId());
            event.setSymbol(order.getSymbol());
            persist(event);
            return order;
        } finally {
            lock.unlock();
        }
    }

    public Tick injectTick(Tick tick) {
        requireText(tick.getSymbol(), "symbol");
        lock.lock();
        try {
            long seq = symbolTickSeq
                    .computeIfAbsent(tick.getSymbol(), s -> new AtomicLong())
                    .incrementAndGet();
            tick.setSeq(seq);
            if (tick.getTimestamp() <= 0) {
                tick.setTimestamp(System.currentTimeMillis());
            }
            expirePendingConditional(tick);
            activateTriggered(tick);
            executeActiveOrders(tick);
            LoggedEvent tickEvent = baseEvent(EventType.MARKET_TICK, tick.getTimestamp());
            tickEvent.setSymbol(tick.getSymbol());
            tickEvent.setTickSeq(tick.getSeq());
            tickEvent.setReferencePrice(str(tick.getLast()));
            tickEvent.setLimitPrice(str(tick.getBid()));
            tickEvent.setFillPrice(str(tick.getAsk()));
            persist(tickEvent);
            return tick;
        } finally {
            lock.unlock();
        }
    }

    public List<ConditionalOrder> queryConditional(String accountId, String symbol) {
        return orderStore.findConditional(accountId, symbol);
    }

    public ConditionalOrder getConditional(String id) {
        return orderStore.getConditional(id);
    }

    public OrdinaryOrder getOrder(String id) {
        return orderStore.getOrder(id);
    }

    public List<OrdinaryOrder> queryOrders(String accountId, String symbol) {
        return orderStore.findOrders(accountId, symbol);
    }

    public List<TriggerHistoryView> history(String accountId, String symbol) {
        List<TriggerHistoryView> views = new ArrayList<>();
        for (LoggedEvent event : orderStore.allEvents()) {
            if (accountId != null && !accountId.equals(event.getAccountId())) {
                continue;
            }
            if (symbol != null && !symbol.equals(event.getSymbol())) {
                continue;
            }
            views.add(toView(event));
        }
        return views;
    }

    private void expirePendingConditional(Tick tick) {
        for (ConditionalOrder order : orderStore.pendingBySymbol(tick.getSymbol())) {
            if (order.getExpireAt() != null && tick.getTimestamp() > order.getExpireAt()) {
                order.setStatus(ConditionalStatus.EXPIRED);
                order.setUpdatedAt(tick.getTimestamp());
                LoggedEvent event = baseEvent(EventType.CONDITIONAL_EXPIRED, tick.getTimestamp());
                event.setConditionalOrderId(order.getId());
                event.setAccountId(order.getAccountId());
                event.setSymbol(order.getSymbol());
                event.setVersion(order.getVersion());
                event.setTickSeq(tick.getSeq());
                persist(event);
            }
        }
    }

    /**
     * Activation and child-order placement are a single lock-held, durable
     * transition. With trigger vs. modify/cancel there is therefore exactly one
     * outcome, never a partially applied intermediate state.
     */
    private void activateTriggered(Tick tick) {
        for (ConditionalOrder order : orderStore.pendingBySymbol(tick.getSymbol())) {
            BigDecimal reference = evaluator.referencePrice(tick, order.getPriceSource());
            if (!evaluator.isTriggered(order, reference)) {
                continue;
            }
            long now = tick.getTimestamp();
            OrdinaryOrder child = new OrdinaryOrder();
            child.setId("O-" + UUID.randomUUID());
            child.setAccountId(order.getAccountId());
            child.setSymbol(order.getSymbol());
            child.setSide(order.getSide());
            child.setExecutionType(order.getExecutionType());
            child.setLimitPrice(order.getLimitPrice());
            child.setQuantity(order.getQuantity());
            child.setMaxSlippageBps(order.getMaxSlippageBps());
            child.setExpireAt(order.getExpireAt());
            child.setStatus(OrderStatus.NEW);
            child.setCreatedAt(now);
            child.setUpdatedAt(now);
            child.setConditionalOrderId(order.getId());

            LoggedEvent activation = baseEvent(EventType.CONDITIONAL_ACTIVATED, now);
            activation.setConditionalOrderId(order.getId());
            activation.setOrderId(child.getId());
            activation.setAccountId(order.getAccountId());
            activation.setSymbol(order.getSymbol());
            activation.setSide(order.getSide().name());
            activation.setVersion(order.getVersion());
            activation.setTickSeq(tick.getSeq());
            activation.setTriggerPrice(str(order.getTriggerPrice()));
            activation.setReferencePrice(str(reference));
            persist(activation);

            persist(orderPlacedEvent(child, now));
            orderStore.putOrder(child);

            order.setStatus(ConditionalStatus.ACTIVATED);
            order.setActivatedAt(now);
            order.setActivatedTickSeq(tick.getSeq());
            order.setActivatedReferencePrice(str(reference));
            order.setChildOrderId(child.getId());
            order.setUpdatedAt(now);

            applyExecution(child, tick, true);
        }
    }

    private void executeActiveOrders(Tick tick) {
        for (OrdinaryOrder order : orderStore.activeBySymbol(tick.getSymbol())) {
            applyExecution(order, tick, order.getFirstSeenTickSeq() == null);
        }
    }

    /**
     * Evaluates one active ordinary order against the tick and durably records
     * the terminal transition. {@code firstTick} marks the tick on which a
     * conditional child is born: the trigger reference price is the slippage
     * anchor, so an instantaneous gap past the cap is rejected rather than chased.
     */
    private void applyExecution(OrdinaryOrder order, Tick tick, boolean firstTick) {
        if (firstTick) {
            order.setFirstSeenTickSeq(tick.getSeq());
            order.setReferencePrice(null);
            if (order.getConditionalOrderId() != null) {
                ConditionalOrder parent = orderStore.getConditional(order.getConditionalOrderId());
                if (parent != null) {
                    order.setReferencePrice(parent.getActivatedReferencePrice());
                }
            }
        }
        ExecutionDecision decision = executor.evaluate(order, tick);
        long now = tick.getTimestamp();
        switch (decision.getKind()) {
            case FILLED -> {
                order.setStatus(OrderStatus.FILLED);
                order.setFillPrice(str(decision.getFillPrice()));
                order.setFilledAt(now);
                order.setFilledTickSeq(tick.getSeq());
                order.setUpdatedAt(now);
                LoggedEvent event = baseEvent(EventType.ORDER_FILLED, now);
                event.setOrderId(order.getId());
                event.setConditionalOrderId(order.getConditionalOrderId());
                event.setAccountId(order.getAccountId());
                event.setSymbol(order.getSymbol());
                event.setTickSeq(tick.getSeq());
                event.setFillPrice(str(decision.getFillPrice()));
                event.setSlipBps(decision.getSlipBps());
                persist(event);
                if (order.getConditionalOrderId() != null) {
                    ConditionalOrder parent = orderStore.getConditional(order.getConditionalOrderId());
                    if (parent != null && parent.getStatus() == ConditionalStatus.ACTIVATED) {
                        parent.setStatus(ConditionalStatus.FILLED);
                        parent.setUpdatedAt(now);
                    }
                }
            }
            case REJECTED_SLIPPAGE -> {
                order.setStatus(OrderStatus.REJECTED);
                order.setUpdatedAt(now);
                LoggedEvent event = baseEvent(EventType.ORDER_REJECTED, now);
                event.setOrderId(order.getId());
                event.setConditionalOrderId(order.getConditionalOrderId());
                event.setAccountId(order.getAccountId());
                event.setSymbol(order.getSymbol());
                event.setTickSeq(tick.getSeq());
                event.setExecutionPrice(str(decision.getFillPrice()));
                event.setSlipBps(decision.getSlipBps());
                event.setMaxSlippageBps(order.getMaxSlippageBps());
                event.setReason("SLIPPAGE_EXCEEDED");
                event.setDetail(decision.getReason());
                persist(event);
                if (order.getConditionalOrderId() != null) {
                    ConditionalOrder parent = orderStore.getConditional(order.getConditionalOrderId());
                    if (parent != null && parent.getStatus() == ConditionalStatus.ACTIVATED) {
                        parent.setStatus(ConditionalStatus.REJECTED_SLIPPAGE);
                        parent.setUpdatedAt(now);
                    }
                }
            }
            case EXPIRED -> {
                order.setStatus(OrderStatus.EXPIRED);
                order.setUpdatedAt(now);
                LoggedEvent event = baseEvent(EventType.ORDER_EXPIRED, now);
                event.setOrderId(order.getId());
                event.setConditionalOrderId(order.getConditionalOrderId());
                event.setAccountId(order.getAccountId());
                event.setSymbol(order.getSymbol());
                event.setTickSeq(tick.getSeq());
                persist(event);
                if (order.getConditionalOrderId() != null) {
                    ConditionalOrder parent = orderStore.getConditional(order.getConditionalOrderId());
                    if (parent != null && parent.getStatus() == ConditionalStatus.ACTIVATED) {
                        parent.setStatus(ConditionalStatus.EXPIRED);
                        parent.setUpdatedAt(now);
                    }
                }
            }
            case STILL_ACTIVE -> {
            }
        }
    }

    private LoggedEvent orderPlacedEvent(OrdinaryOrder order, long now) {
        LoggedEvent event = baseEvent(EventType.ORDER_PLACED, now);
        event.setOrderId(order.getId());
        event.setConditionalOrderId(order.getConditionalOrderId());
        event.setAccountId(order.getAccountId());
        event.setSymbol(order.getSymbol());
        event.setSide(order.getSide().name());
        event.setExecutionType(order.getExecutionType().name());
        event.setLimitPrice(str(order.getLimitPrice()));
        event.setQuantity(order.getQuantity());
        event.setMaxSlippageBps(order.getMaxSlippageBps());
        event.setExpireAt(order.getExpireAt());
        return event;
    }

    private ConditionalOrder pendingConditional(String id) {
        ConditionalOrder order = orderStore.getConditional(id);
        if (order == null) {
            throw new NotFoundException("conditional order not found: " + id);
        }
        if (order.getStatus() != ConditionalStatus.PENDING) {
            throw new ConflictException("conditional order not modifiable: " + order.getStatus());
        }
        return order;
    }

    private LoggedEvent baseEvent(EventType type, long now) {
        LoggedEvent event = new LoggedEvent();
        event.setType(type);
        event.setTimestamp(now);
        return event;
    }

    private void persist(LoggedEvent event) {
        eventStore.append(event);
        orderStore.appendEvent(event);
    }

    private TriggerHistoryView toView(LoggedEvent event) {
        TriggerHistoryView view = new TriggerHistoryView();
        view.setEventSeq(event.getSeq());
        view.setTimestamp(event.getTimestamp());
        view.setType(event.getType().name());
        view.setConditionalOrderId(event.getConditionalOrderId());
        view.setOrderId(event.getOrderId());
        view.setSymbol(event.getSymbol());
        view.setTickSeq(event.getTickSeq() == 0 ? null : event.getTickSeq());
        view.setTriggerPrice(event.getTriggerPrice());
        view.setReferencePrice(event.getReferencePrice());
        view.setExecutionPrice(event.getExecutionPrice());
        view.setFillPrice(event.getFillPrice());
        view.setMaxSlippageBps(event.getType() == EventType.ORDER_REJECTED
                ? event.getMaxSlippageBps() : null);
        view.setActualSlipBps(event.getSlipBps() == 0 ? null : event.getSlipBps());
        view.setReason(event.getReason());
        view.setDetail(event.getDetail());
        return view;
    }

    private static String str(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required");
        }
    }

    private static void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new ValidationException(field + " is required");
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (!isPositive(value)) {
            throw new ValidationException(field + " must be positive");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new ValidationException(field + " must be positive");
        }
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
