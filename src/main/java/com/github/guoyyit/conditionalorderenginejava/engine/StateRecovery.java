package com.github.guoyyit.conditionalorderenginejava.engine;

import com.github.guoyyit.conditionalorderenginejava.store.EventStore;
import com.github.guoyyit.conditionalorderenginejava.store.EventType;
import com.github.guoyyit.conditionalorderenginejava.store.InMemoryOrderStore;
import com.github.guoyyit.conditionalorderenginejava.store.LoggedEvent;
import com.github.guoyyit.conditionalorderenginejava.domain.ConditionalStatus;
import com.github.guoyyit.conditionalorderenginejava.domain.ExecutionType;
import com.github.guoyyit.conditionalorderenginejava.domain.OrderStatus;
import com.github.guoyyit.conditionalorderenginejava.domain.PriceSource;
import com.github.guoyyit.conditionalorderenginejava.domain.Side;
import com.github.guoyyit.conditionalorderenginejava.domain.ConditionalType;
import com.github.guoyyit.conditionalorderenginejava.domain.TriggerDirection;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.OrdinaryOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds in-memory state from the durable event log on startup.
 * Replay is idempotent: it never re-triggers or re-fills an order.
 */
@Component
public class StateRecovery {

    private static final Logger log = LoggerFactory.getLogger(StateRecovery.class);

    private final EventStore eventStore;
    private final InMemoryOrderStore orderStore;
    private final OrderExecutor executor;

    public StateRecovery(EventStore eventStore, InMemoryOrderStore orderStore) {
        this.eventStore = eventStore;
        this.orderStore = orderStore;
        this.executor = new OrderExecutor();
    }

    @PostConstruct
    public void recover() {
        List<LoggedEvent> events = eventStore.readAll();
        List<ConditionalOrder> conditionalOrders = new ArrayList<>();
        List<OrdinaryOrder> orders = new ArrayList<>();
        for (LoggedEvent event : events) {
            replay(event, conditionalOrders, orders);
        }
        completeInterruptedActivations(conditionalOrders, orders, events);
        orderStore.replaceState(conditionalOrders, orders, events);
        log.info("Recovered {} event(s): {} conditional order(s), {} ordinary order(s)",
                events.size(), conditionalOrders.size(), orders.size());
    }

    /**
     * Handles a crash between activation and the child order's terminal event:
     * a market child is completed deterministically at its durable trigger
     * reference price. Recovery events are appended before replacing state so
     * the in-memory view and the log agree; replay stays idempotent because only
     * orders with no terminal outcome are completed.
     */
    private void completeInterruptedActivations(List<ConditionalOrder> conditionalOrders,
                                                List<OrdinaryOrder> orders,
                                                List<LoggedEvent> events) {
        for (ConditionalOrder parent : conditionalOrders) {
            if (parent.getStatus() != ConditionalStatus.ACTIVATED || parent.getChildOrderId() == null) {
                continue;
            }
            OrdinaryOrder child = orders.stream()
                    .filter(o -> o.getId().equals(parent.getChildOrderId()))
                    .findFirst()
                    .orElse(null);
            if (child == null || child.getStatus() != OrderStatus.NEW) {
                continue;
            }
            child.setReferencePrice(parent.getActivatedReferencePrice());
            child.setFirstSeenTickSeq(parent.getActivatedTickSeq());
            ExecutionDecision decision = executor.recoverAtReference(child);
            if (decision.getKind() != ExecutionDecision.Kind.FILLED) {
                continue;
            }
            child.setStatus(OrderStatus.FILLED);
            child.setFillPrice(decision.getFillPrice().toPlainString());
            child.setFilledAt(parent.getActivatedAt());
            child.setFilledTickSeq(parent.getActivatedTickSeq());
            child.setUpdatedAt(parent.getActivatedAt());
            parent.setStatus(ConditionalStatus.FILLED);

            LoggedEvent fill = new LoggedEvent();
            fill.setType(EventType.ORDER_FILLED);
            fill.setTimestamp(parent.getActivatedAt());
            fill.setOrderId(child.getId());
            fill.setConditionalOrderId(parent.getId());
            fill.setAccountId(child.getAccountId());
            fill.setSymbol(child.getSymbol());
            fill.setTickSeq(parent.getActivatedTickSeq());
            fill.setFillPrice(child.getFillPrice());
            fill.setSlipBps(decision.getSlipBps());
            fill.setReason("RECOVERED");
            eventStore.append(fill);
            events.add(fill);
        }
    }

    private void replay(LoggedEvent event,
                        List<ConditionalOrder> conditionalOrders,
                        List<OrdinaryOrder> orders) {
        switch (event.getType()) {
            case CONDITIONAL_CREATED -> put(conditionalOrders, replayConditional(event));
            case CONDITIONAL_MODIFIED -> {
                ConditionalOrder order = find(conditionalOrders, event.getConditionalOrderId());
                applyModified(order, event);
            }
            case CONDITIONAL_CANCELLED ->
                    find(conditionalOrders, event.getConditionalOrderId()).setStatus(ConditionalStatus.CANCELLED);
            case CONDITIONAL_EXPIRED ->
                    find(conditionalOrders, event.getConditionalOrderId()).setStatus(ConditionalStatus.EXPIRED);
            case CONDITIONAL_ACTIVATED ->
                    applyActivated(find(conditionalOrders, event.getConditionalOrderId()),
                            event, orders);
            case ORDER_PLACED -> {
                OrdinaryOrder placed = replayOrder(event);
                put(orders, placed);
                if (placed.getConditionalOrderId() != null) {
                    conditionalOrders.stream()
                            .filter(parent -> parent.getId().equals(placed.getConditionalOrderId())
                                    && event.getOrderId().equals(parent.getChildOrderId()))
                            .findFirst()
                            .ifPresent(parent -> {
                                placed.setFirstSeenTickSeq(parent.getActivatedTickSeq());
                                placed.setReferencePrice(parent.getActivatedReferencePrice());
                            });
                }
            }
            case ORDER_FILLED -> applyFilled(conditionalOrders, findOrder(orders, event.getOrderId()), event);
            case ORDER_CANCELLED ->
                    applyCancelled(conditionalOrders, findOrder(orders, event.getOrderId()), event);
            case ORDER_EXPIRED ->
                    applyExpired(conditionalOrders, findOrder(orders, event.getOrderId()), event);
            case ORDER_REJECTED ->
                    applyRejected(conditionalOrders, findOrder(orders, event.getOrderId()), event);
            case MARKET_TICK -> {
            }
        }
    }

    private void applyRejected(List<ConditionalOrder> conditionalOrders,
                               OrdinaryOrder order, LoggedEvent event) {
        order.setStatus(OrderStatus.REJECTED);
        order.setUpdatedAt(event.getTimestamp());
        if (order.getConditionalOrderId() != null) {
            conditionalOrders.stream()
                    .filter(parent -> parent.getId().equals(order.getConditionalOrderId()))
                    .findFirst()
                    .ifPresent(parent -> parent.setStatus(ConditionalStatus.REJECTED_SLIPPAGE));
        }
    }

    private ConditionalOrder replayConditional(LoggedEvent event) {
        ConditionalOrder order = new ConditionalOrder();
        order.setId(event.getConditionalOrderId());
        order.setVersion(event.getVersion());
        order.setAccountId(event.getAccountId());
        order.setSymbol(event.getSymbol());
        order.setSide(Side.valueOf(event.getSide()));
        order.setType(ConditionalType.valueOf(event.getConditionalType()));
        order.setDirection(TriggerDirection.valueOf(event.getDirection()));
        order.setPriceSource(PriceSource.valueOf(event.getPriceSource()));
        order.setExecutionType(ExecutionType.valueOf(event.getExecutionType()));
        order.setTriggerPrice(decimal(event.getTriggerPrice()));
        order.setQuantity(event.getQuantity());
        order.setMaxSlippageBps(event.getMaxSlippageBps());
        order.setLimitPrice(decimal(event.getLimitPrice()));
        order.setExpireAt(event.getExpireAt());
        order.setCreatedAt(event.getTimestamp());
        order.setUpdatedAt(event.getTimestamp());
        order.setStatus(ConditionalStatus.PENDING);
        return order;
    }

    private OrdinaryOrder replayOrder(LoggedEvent event) {
        OrdinaryOrder order = new OrdinaryOrder();
        order.setId(event.getOrderId());
        order.setAccountId(event.getAccountId());
        order.setSymbol(event.getSymbol());
        order.setSide(Side.valueOf(event.getSide()));
        order.setExecutionType(ExecutionType.valueOf(event.getExecutionType()));
        order.setLimitPrice(decimal(event.getLimitPrice()));
        order.setQuantity(event.getQuantity());
        order.setMaxSlippageBps(event.getMaxSlippageBps());
        order.setExpireAt(event.getExpireAt());
        order.setCreatedAt(event.getTimestamp());
        order.setUpdatedAt(event.getTimestamp());
        order.setStatus(OrderStatus.NEW);
        order.setConditionalOrderId(event.getConditionalOrderId());
        if (event.getFirstSeenTickSeq() != null) {
            order.setFirstSeenTickSeq(event.getFirstSeenTickSeq());
        }
        order.setReferencePrice(event.getExecutionPrice());
        return order;
    }

    private void applyModified(ConditionalOrder order, LoggedEvent event) {
        order.setVersion(event.getVersion());
        order.setUpdatedAt(event.getTimestamp());
        if (event.getTriggerPrice() != null) {
            order.setTriggerPrice(decimal(event.getTriggerPrice()));
        }
        if (event.getQuantity() > 0) {
            order.setQuantity(event.getQuantity());
        }
        if (event.getMaxSlippageBps() > 0) {
            order.setMaxSlippageBps(event.getMaxSlippageBps());
        }
        if (event.getLimitPrice() != null) {
            order.setLimitPrice(decimal(event.getLimitPrice()));
        }
        if (event.getDirection() != null) {
            order.setDirection(TriggerDirection.valueOf(event.getDirection()));
        }
        if (event.getPriceSource() != null) {
            order.setPriceSource(PriceSource.valueOf(event.getPriceSource()));
        }
        if (event.getExecutionType() != null) {
            order.setExecutionType(ExecutionType.valueOf(event.getExecutionType()));
        }
        if (event.getExpireAt() != null) {
            order.setExpireAt(event.getExpireAt());
        }
    }

    private void applyActivated(ConditionalOrder order, LoggedEvent event,
                                List<OrdinaryOrder> orders) {
        order.setStatus(ConditionalStatus.ACTIVATED);
        order.setActivatedAt(event.getTimestamp());
        order.setActivatedTickSeq(event.getTickSeq());
        order.setActivatedReferencePrice(event.getReferencePrice());
        order.setChildOrderId(event.getOrderId());
        orders.stream()
                .filter(child -> child.getId().equals(event.getOrderId()))
                .findFirst()
                .ifPresent(child -> {
                    child.setFirstSeenTickSeq(event.getTickSeq());
                    child.setReferencePrice(event.getReferencePrice());
                });
    }

    private void applyFilled(List<ConditionalOrder> conditionalOrders,
                             OrdinaryOrder order, LoggedEvent event) {
        order.setStatus(OrderStatus.FILLED);
        order.setFillPrice(event.getFillPrice());
        order.setFilledAt(event.getTimestamp());
        order.setFilledTickSeq(event.getTickSeq());
        order.setUpdatedAt(event.getTimestamp());
        propagateParent(conditionalOrders, order, ConditionalStatus.FILLED, event.getTimestamp());
    }

    private void applyCancelled(List<ConditionalOrder> conditionalOrders,
                                OrdinaryOrder order, LoggedEvent event) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(event.getTimestamp());
        propagateParent(conditionalOrders, order, ConditionalStatus.CANCELLED, event.getTimestamp());
    }

    private void applyExpired(List<ConditionalOrder> conditionalOrders,
                              OrdinaryOrder order, LoggedEvent event) {
        order.setStatus(OrderStatus.EXPIRED);
        order.setUpdatedAt(event.getTimestamp());
        propagateParent(conditionalOrders, order, ConditionalStatus.EXPIRED, event.getTimestamp());
    }

    private void propagateParent(List<ConditionalOrder> conditionalOrders,
                                 OrdinaryOrder order, ConditionalStatus status, long timestamp) {
        if (order.getConditionalOrderId() == null) {
            return;
        }
        conditionalOrders.stream()
                .filter(parent -> parent.getId().equals(order.getConditionalOrderId()))
                .findFirst()
                .ifPresent(parent -> {
                    parent.setStatus(status);
                    parent.setUpdatedAt(timestamp);
                });
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static <T> void put(List<T> list, T item) {
        list.removeIf(existing -> idOf(existing).equals(idOf(item)));
        list.add(item);
    }

    private static String idOf(Object item) {
        if (item instanceof ConditionalOrder c) {
            return c.getId();
        }
        return ((OrdinaryOrder) item).getId();
    }

    private static ConditionalOrder find(List<ConditionalOrder> orders, String id) {
        return orders.stream().filter(o -> o.getId().equals(id)).findFirst().orElseThrow();
    }

    private static OrdinaryOrder findOrder(List<OrdinaryOrder> orders, String id) {
        return orders.stream().filter(o -> o.getId().equals(id)).findFirst().orElseThrow();
    }
}
