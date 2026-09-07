package xyz.wewin.autumn.gateway.limit.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * limit-autumn-gateway 全局默认限流参数。
 * 路由过滤器 args 未配置时，使用这里提供的默认值。
 */
@ConfigurationProperties(prefix = LimitAutumnGatewayProperties.PREFIX)
public class LimitAutumnGatewayProperties {

    public static final String PREFIX = "limit.autumn-gateway";

    /** 是否启用限流过滤器与真实 IP 过滤器 */
    private boolean enabled = true;

    /** 默认令牌补充速率（个/窗口） */
    private int replenishRate = 1;

    /** 默认桶容量（突发上限） */
    private int burstCapacity = 2;

    /** 默认时间窗口（秒） */
    private long timeWindowSeconds = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getReplenishRate() {
        return replenishRate;
    }

    public void setReplenishRate(int replenishRate) {
        this.replenishRate = replenishRate;
    }

    public int getBurstCapacity() {
        return burstCapacity;
    }

    public void setBurstCapacity(int burstCapacity) {
        this.burstCapacity = burstCapacity;
    }

    public long getTimeWindowSeconds() {
        return timeWindowSeconds;
    }

    public void setTimeWindowSeconds(long timeWindowSeconds) {
        this.timeWindowSeconds = timeWindowSeconds;
    }
}
