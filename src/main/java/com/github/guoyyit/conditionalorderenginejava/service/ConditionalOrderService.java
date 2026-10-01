package com.github.guoyyit.conditionalorderenginejava.service;

import com.github.guoyyit.conditionalorderenginejava.model.ConditionalOrder;
import com.github.guoyyit.conditionalorderenginejava.model.MarketQuote;
import com.github.guoyyit.conditionalorderenginejava.model.TriggerRecord;

import java.util.List;
import java.util.Optional;

/**
 * 条件订单服务门面：提交、修改、取消、行情驱动触发、查询。
 * 所有状态变更串行化，保证修改/取消与触发并发时结果确定。
 */
public interface ConditionalOrderService {

    ConditionalOrder submit(ConditionalOrder order);

    /** 激活前修改参数；若已激活则拒绝并返回当前订单 */
    ConditionalOrder modify(String orderId, ConditionalOrder newParams);

    /** 任意时刻取消；已终态的订单返回 false */
    boolean cancel(String orderId);

    /** 注入行情，驱动触发判定与执行 */
    void onMarketQuote(MarketQuote quote);

    Optional<ConditionalOrder> findById(String orderId);

    List<ConditionalOrder> findByAccount(String accountId);

    List<ConditionalOrder> findByAccountAndSymbol(String accountId, String symbol);

    List<TriggerRecord> findTriggerHistory(String accountId, String symbol);
}
