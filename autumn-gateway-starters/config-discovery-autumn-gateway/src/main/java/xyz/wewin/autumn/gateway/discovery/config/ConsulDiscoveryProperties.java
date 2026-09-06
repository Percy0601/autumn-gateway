package xyz.wewin.autumn.gateway.discovery.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = ConsulDiscoveryProperties.PREFIX)
public class ConsulDiscoveryProperties {

    public static final String PREFIX = "consul.discovery";

    private boolean enabled = true;

    private String host = "localhost";
    private int port = 8500;
    private String scheme = "http";
    private String token;
    private Duration timeout = Duration.ofSeconds(5);

    private String serviceName;
    private String instanceId;
    private String hostname;
    private String ip;
    private int portOverride = -1;

    private boolean register = true;
    private boolean deregister = true;

    private List<String> tags = List.of();
    private Map<String, String> metadata = Map.of();

    private String healthCheckPath = "/actuator/health";
    private Duration healthCheckInterval = Duration.ofSeconds(15);
    private Duration healthCheckTimeout = Duration.ofSeconds(3);
    private String healthCheckProtocol;

    private boolean watchEnabled = true;
    private Duration watchDelay = Duration.ofSeconds(30);

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

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPortOverride() {
        return portOverride;
    }

    public void setPortOverride(int portOverride) {
        this.portOverride = portOverride;
    }

    public boolean isRegister() {
        return register;
    }

    public void setRegister(boolean register) {
        this.register = register;
    }

    public boolean isDeregister() {
        return deregister;
    }

    public void setDeregister(boolean deregister) {
        this.deregister = deregister;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String getHealthCheckPath() {
        return healthCheckPath;
    }

    public void setHealthCheckPath(String healthCheckPath) {
        this.healthCheckPath = healthCheckPath;
    }

    public Duration getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public void setHealthCheckInterval(Duration healthCheckInterval) {
        this.healthCheckInterval = healthCheckInterval;
    }

    public Duration getHealthCheckTimeout() {
        return healthCheckTimeout;
    }

    public void setHealthCheckTimeout(Duration healthCheckTimeout) {
        this.healthCheckTimeout = healthCheckTimeout;
    }

    public String getHealthCheckProtocol() {
        return healthCheckProtocol;
    }

    public void setHealthCheckProtocol(String healthCheckProtocol) {
        this.healthCheckProtocol = healthCheckProtocol;
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