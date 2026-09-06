package xyz.wewin.autumn.gateway.discovery.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Consul 配置中心连接与行为参数（前缀 {@code consul.config}）。
 *
 * <p>KV 布局约定：与 spring-cloud-consul-config 保持一致的层级语义——
 * <pre>
 *   {prefix}/application/{propertyKey}  全局默认配置（所有应用共享）
 *   {prefix}/{application}/{propertyKey} 当前应用配置（优先级更高）
 * </pre>
 * 例如 KV：{@code config/order-service/server.port=8080} 会以 property
 * {@code server.port=8080} 注入 Environment，@Value / @ConfigurationProperties 均可直接使用。</p>
 */
@ConfigurationProperties(prefix = ConsulConfigProperties.PREFIX)
public class ConsulConfigProperties {

    public static final String PREFIX = "consul.config";

    /** 总开关 */
    private boolean enabled = true;

    // ===== Consul 连接 =====
    private String host = "localhost";
    private int port = 8500;
    private String scheme = "http";
    /** ACL Token（可选，不填则不携带 X-Consul-Token） */
    private String token;
    private Duration timeout = Duration.ofSeconds(3);

    // ===== KV 布局 =====
    /** KV 根前缀，默认 {@code config} */
    private String prefix = "config";
    /** 应用名，默认取 {@code spring.application.name} */
    private String application;
    /** 启动拉取失败是否直接失败（false 则跳过继续启动） */
    private boolean failFast = true;

    // ===== 动态刷新（Blocking Query 长轮询） =====
    /** 是否启用配置变更自动刷新 */
    private boolean watchEnabled = true;
    /** 刷新轮询周期（同时作为长轮询兜底） */
    private Duration watchDelay = Duration.ofSeconds(120);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean isWatchEnabled() {
        return watchEnabled;
    }

    public void setWatchEnabled(boolean watchEnabled) {
        this.watchEnabled = watchEnabled;
    }

    public Duration getWatchDelay() {
        return watchDelay;
    }

    public void setWatchDelay(Duration watchDelay) {
        this.watchDelay = watchDelay;
    }
}
