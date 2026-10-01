package com.github.guoyyit.conditionalorderenginejava.web;

import com.github.guoyyit.conditionalorderenginejava.domain.Tick;
import com.github.guoyyit.conditionalorderenginejava.dto.CancelRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.CreateConditionalRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.CreateOrderRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.ModifyConditionalRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.TickRequest;
import com.github.guoyyit.conditionalorderenginejava.dto.TriggerHistoryView;
import com.github.guoyyit.conditionalorderenginejava.engine.NotFoundException;
import com.github.guoyyit.conditionalorderenginejava.engine.TradingEngine;
import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.OrdinaryOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for conditional orders, direct orders, local tick injection
 * and audit/trigger history queries.
 */
@RestController
@RequestMapping("/api")
public class TradingController {

    private final TradingEngine engine;

    public TradingController(TradingEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/conditional-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ConditionalOrder createConditional(@RequestBody CreateConditionalRequest request) {
        return engine.createConditional(request);
    }

    @GetMapping("/conditional-orders/{id}")
    public ConditionalOrder getConditional(@PathVariable String id) {
        ConditionalOrder order = engine.getConditional(id);
        if (order == null) {
            throw new NotFoundException("conditional order not found: " + id);
        }
        return order;
    }

    @PutMapping("/conditional-orders/{id}")
    public ConditionalOrder modifyConditional(@PathVariable String id,
                                              @RequestBody ModifyConditionalRequest request) {
        return engine.modifyConditional(id, request);
    }

    @PostMapping("/conditional-orders/{id}/cancel")
    public ConditionalOrder cancelConditional(@PathVariable String id,
                                              @RequestBody(required = false) CancelRequest request) {
        return engine.cancelConditional(id, request);
    }

    @GetMapping("/conditional-orders")
    public List<ConditionalOrder> listConditional(@RequestParam(required = false) String accountId,
                                                  @RequestParam(required = false) String symbol) {
        return engine.queryConditional(accountId, symbol);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrdinaryOrder placeOrder(@RequestBody CreateOrderRequest request) {
        return engine.placeOrder(request);
    }

    @GetMapping("/orders/{id}")
    public OrdinaryOrder getOrder(@PathVariable String id) {
        OrdinaryOrder order = engine.getOrder(id);
        if (order == null) {
            throw new NotFoundException("order not found: " + id);
        }
        return order;
    }

    @PostMapping("/orders/{id}/cancel")
    public OrdinaryOrder cancelOrder(@PathVariable String id,
                                     @RequestBody(required = false) CancelRequest request) {
        return engine.cancelOrder(id, request);
    }

    @GetMapping("/orders")
    public List<OrdinaryOrder> listOrders(@RequestParam(required = false) String accountId,
                                          @RequestParam(required = false) String symbol) {
        return engine.queryOrders(accountId, symbol);
    }

    @PostMapping("/market/ticks")
    public Tick injectTick(@RequestBody TickRequest request) {
        Tick tick = new Tick();
        tick.setSymbol(request.getSymbol());
        tick.setTimestamp(request.getTimestamp() == null ? 0L : request.getTimestamp());
        tick.setLast(request.getLast());
        tick.setBid(request.getBid());
        tick.setAsk(request.getAsk());
        return engine.injectTick(tick);
    }

    @GetMapping("/history")
    public List<TriggerHistoryView> history(@RequestParam(required = false) String accountId,
                                            @RequestParam(required = false) String symbol) {
        return engine.history(accountId, symbol);
    }
}
