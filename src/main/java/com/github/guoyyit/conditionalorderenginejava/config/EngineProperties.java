package com.github.guoyyit.conditionalorderenginejava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 条件订单引擎配置项。
 */
@Component
@ConfigurationProperties(prefix = "conditional-engine")
public class EngineProperties {

    /** 事件日志存储目录 */
    private String storeDir = "data/event-log";

    /** 市价单默认滑点上限（绝对价格偏差），订单未指定时使用 */
    private BigDecimal defaultMaxSlippage = new BigDecimal("0.05");

    public String getStoreDir() {
        return storeDir;
    }

    public void setStoreDir(String storeDir) {
        this.storeDir = storeDir;
    }

    public BigDecimal getDefaultMaxSlippage() {
        return defaultMaxSlippage;
    }

    public void setDefaultMaxSlippage(BigDecimal defaultMaxSlippage) {
        this.defaultMaxSlippage = defaultMaxSlippage;
    }
}
