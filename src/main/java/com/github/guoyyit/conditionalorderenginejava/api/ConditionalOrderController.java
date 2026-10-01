package com.github.guoyyit.conditionalorderenginejava.api;

import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.MarketQuote;
import com.github.guoyyit.conditionalorderenginejava.model.TriggerRecord;
import com.github.guoyyit.conditionalorderenginejava.service.ConditionalOrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conditional-orders")
public class ConditionalOrderController {

    private final ConditionalOrderService service;

    public ConditionalOrderController(ConditionalOrderService service) {
        this.service = service;
    }

    @PostMapping
    public ConditionalOrder submit(@RequestBody ConditionalOrder order) {
        return service.submit(order);
    }

    @PutMapping("/{orderId}")
    public ConditionalOrder modify(@PathVariable String orderId, @RequestBody ConditionalOrder newParams) {
        return service.modify(orderId, newParams);
    }

    @DeleteMapping("/{orderId}")
    public boolean cancel(@PathVariable String orderId) {
        return service.cancel(orderId);
    }

    @PostMapping("/market-quotes")
    public void onMarketQuote(@RequestBody MarketQuote quote) {
        service.onMarketQuote(quote);
    }

    @GetMapping
    public List<ConditionalOrder> query(@RequestParam String accountId,
                                        @RequestParam(required = false) String symbol) {
        if (symbol == null) {
            return service.findByAccount(accountId);
        }
        return service.findByAccountAndSymbol(accountId, symbol);
    }

    @GetMapping("/trigger-history")
    public List<TriggerRecord> triggerHistory(@RequestParam String accountId,
                                              @RequestParam(required = false) String symbol) {
        return service.findTriggerHistory(accountId, symbol);
    }
}
