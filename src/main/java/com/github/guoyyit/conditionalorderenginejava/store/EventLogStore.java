package com.github.guoyyit.conditionalorderenginejava.store;

import java.util.List;

/**
 * 事件日志存储：追加写入 + 崩溃恢复重放。
 */
public interface EventLogStore {

    void append(OrderEvent event);

    List<OrderEvent> loadAll();

    void close();
}
