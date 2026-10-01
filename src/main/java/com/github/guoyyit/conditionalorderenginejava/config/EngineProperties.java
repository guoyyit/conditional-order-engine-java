package com.github.guoyyit.conditionalorderenginejava.config;

import com.github.guoyyit.conditionalorderenginejava.domain.PriceSource;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties under the {@code coengine} prefix.
 */
@ConfigurationProperties(prefix = "coengine")
public class EngineProperties {

    /** Directory of the durable event log. */
    private String dataDir = "data/coengine";

    /** File name of the append-only event log. */
    private String eventLogFile = "events.log";

    /** Default price source used when a conditional order does not specify one. */
    private PriceSource defaultPriceSource = PriceSource.LAST;

    /** Default slippage cap in basis points for market orders without explicit value. */
    private long defaultMaxSlippageBps = 50L;

    /** Whether every event append is fsynced before acknowledgement. */
    private boolean fsyncPerAppend = true;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getEventLogFile() {
        return eventLogFile;
    }

    public void setEventLogFile(String eventLogFile) {
        this.eventLogFile = eventLogFile;
    }

    public PriceSource getDefaultPriceSource() {
        return defaultPriceSource;
    }

    public void setDefaultPriceSource(PriceSource defaultPriceSource) {
        this.defaultPriceSource = defaultPriceSource;
    }

    public long getDefaultMaxSlippageBps() {
        return defaultMaxSlippageBps;
    }

    public void setDefaultMaxSlippageBps(long defaultMaxSlippageBps) {
        this.defaultMaxSlippageBps = defaultMaxSlippageBps;
    }

    public boolean isFsyncPerAppend() {
        return fsyncPerAppend;
    }

    public void setFsyncPerAppend(boolean fsyncPerAppend) {
        this.fsyncPerAppend = fsyncPerAppend;
    }
}
