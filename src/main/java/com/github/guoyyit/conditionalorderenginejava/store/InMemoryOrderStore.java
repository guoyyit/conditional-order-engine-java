package com.github.guoyyit.conditionalorderenginejava.store;

import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.OrdinaryOrder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory mutable order state, rebuilt from the event log on startup.
 */
@Component
public class InMemoryOrderStore {

    private final Map<String, ConditionalOrder> conditionalOrders = new ConcurrentHashMap<>();
    private final Map<String, OrdinaryOrder> orders = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<LoggedEvent> events = new CopyOnWriteArrayList<>();

    public void putConditional(ConditionalOrder order) {
        conditionalOrders.put(order.getId(), order);
    }

    public ConditionalOrder getConditional(String id) {
        return conditionalOrders.get(id);
    }

    public List<ConditionalOrder> findConditional(String accountId, String symbol) {
        List<ConditionalOrder> result = new ArrayList<>();
        for (ConditionalOrder order : conditionalOrders.values()) {
            if (accountId != null && !accountId.equals(order.getAccountId())) {
                continue;
            }
            if (symbol != null && !symbol.equals(order.getSymbol())) {
                continue;
            }
            result.add(order);
        }
        result.sort((a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
        return result;
    }

    public List<ConditionalOrder> pendingBySymbol(String symbol) {
        List<ConditionalOrder> result = new ArrayList<>();
        for (ConditionalOrder order : conditionalOrders.values()) {
            if (order.getStatus() == com.github.guoyyit.conditionalorderenginejava.domain.ConditionalStatus.PENDING
                    && symbol.equals(order.getSymbol())) {
                result.add(order);
            }
        }
        result.sort((a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
        return result;
    }

    public void putOrder(OrdinaryOrder order) {
        orders.put(order.getId(), order);
    }

    public OrdinaryOrder getOrder(String id) {
        return orders.get(id);
    }

    public List<OrdinaryOrder> findOrders(String accountId, String symbol) {
        List<OrdinaryOrder> result = new ArrayList<>();
        for (OrdinaryOrder order : orders.values()) {
            if (accountId != null && !accountId.equals(order.getAccountId())) {
                continue;
            }
            if (symbol != null && !symbol.equals(order.getSymbol())) {
                continue;
            }
            result.add(order);
        }
        result.sort((a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
        return result;
    }

    public List<OrdinaryOrder> activeBySymbol(String symbol) {
        List<OrdinaryOrder> result = new ArrayList<>();
        for (OrdinaryOrder order : orders.values()) {
            if (order.getStatus() == com.github.guoyyit.conditionalorderenginejava.domain.OrderStatus.NEW
                    && symbol.equals(order.getSymbol())) {
                result.add(order);
            }
        }
        result.sort((a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
        return result;
    }

    public void appendEvent(LoggedEvent event) {
        events.add(event);
    }

    public List<LoggedEvent> allEvents() {
        return new ArrayList<>(events);
    }

    public void replaceState(List<ConditionalOrder> conditionalOrders,
                             List<OrdinaryOrder> orders,
                             List<LoggedEvent> events) {
        this.conditionalOrders.clear();
        for (ConditionalOrder order : conditionalOrders) {
            this.conditionalOrders.put(order.getId(), order);
        }
        this.orders.clear();
        for (OrdinaryOrder order : orders) {
            this.orders.put(order.getId(), order);
        }
        this.events.clear();
        this.events.addAll(events);
    }
}
