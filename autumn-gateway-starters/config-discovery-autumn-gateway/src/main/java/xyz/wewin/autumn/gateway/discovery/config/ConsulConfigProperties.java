package xyz.wewin.autumn.gateway.discovery.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Consul 配置中心行为参数（前缀 {@code consul.config.*}）。
 * 连接信息由 {@link ConsulProperties}（前缀 {@code consul.*}）统一提供。
 *
 * <p>KV 布局约定：
 * <pre>
 *   {prefix}/application/{propertyKey}  全局默认配置（所有应用共享）
 *   {prefix}/{application}/{propertyKey} 当前应用配置（优先级更高）
 * </pre>
 */
@ConfigurationProperties(prefix = ConsulConfigProperties.PREFIX)
public class ConsulConfigProperties {

    public static final String PREFIX = "consul.config";

    /** 总开关 */
    private boolean enabled = true;

    // ===== KV 布局 =====
    private String prefix = "config";
    private String application;
    private boolean failFast = true;

    // ===== 动态刷新（Blocking Query 长轮询） =====
    private boolean watchEnabled = true;
    /** 单次长轮询最长等待时长。变更发生时阻塞请求会立即返回，故该值只决定"无事发生时"的空闲唤醒
     *  频率（超时返回的是当前全量快照），不影响刷新即时性。Consul 服务端上限 10 分钟，默认 5 分钟；
     *  服务实例多、配置快照大时可调大（如 5m~10m）以显著降低空闲网络流量。 */
    private Duration watchDelay = Duration.ofMinutes(5);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getApplication() { return application; }
    public void setApplication(String application) { this.application = application; }

    public boolean isFailFast() { return failFast; }
    public void setFailFast(boolean failFast) { this.failFast = failFast; }

    public boolean isWatchEnabled() { return watchEnabled; }
    public void setWatchEnabled(boolean watchEnabled) { this.watchEnabled = watchEnabled; }

    public Duration getWatchDelay() { return watchDelay; }
    public void setWatchDelay(Duration watchDelay) { this.watchDelay = watchDelay; }
}