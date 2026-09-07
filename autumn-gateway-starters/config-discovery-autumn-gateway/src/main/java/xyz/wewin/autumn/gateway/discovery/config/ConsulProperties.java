package xyz.wewin.autumn.gateway.discovery.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Consul 顶层共享连接配置（前缀 {@code consul.*}）。
 * config / discovery / registry 共用这一份连接信息。
 * 需要分集群时，可在各自前缀下单独覆盖对应 Properties 的字段（默认优先本类）。
 */
@ConfigurationProperties(prefix = ConsulProperties.PREFIX)
public class ConsulProperties {

    public static final String PREFIX = "consul";

    private String host = "localhost";
    private int port = 8500;
    private String scheme = "http";
    private String token;
    private Duration timeout = Duration.ofSeconds(3);

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getScheme() { return scheme; }
    public void setScheme(String scheme) { this.scheme = scheme; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
}