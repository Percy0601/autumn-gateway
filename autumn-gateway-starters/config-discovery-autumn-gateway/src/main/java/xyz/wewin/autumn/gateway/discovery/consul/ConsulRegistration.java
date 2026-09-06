package xyz.wewin.autumn.gateway.discovery.consul;

import org.jspecify.annotations.Nullable;
import org.springframework.cloud.client.serviceregistry.Registration;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class ConsulRegistration implements Registration {

    private final String serviceId;
    private final String instanceId;
    private final String host;
    private final int port;
    private final boolean secure;
    private final List<String> tags;
    private final Map<String, String> metadata;

    public ConsulRegistration(String serviceId, String instanceId, String host, int port,
                              boolean secure, List<String> tags, Map<String, String> metadata) {
        this.serviceId = serviceId;
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.secure = secure;
        this.tags = tags;
        this.metadata = metadata;
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public @Nullable String getInstanceId() {
        return instanceId;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public URI getUri() {
        return URI.create((secure ? "https" : "http") + "://" + host + ":" + port);
    }

    @Override
    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public List<String> getTags() {
        return tags;
    }

    public ConsulAgentClient.ServiceRegistration toServiceRegistration(String healthCheckUrl, String healthCheckInterval, String healthCheckTimeout, String deregisterCriticalServiceAfter) {
        Map<String, Object> check = null;
        if (healthCheckUrl != null) {
            check = new java.util.HashMap<>();
            check.put("HTTP", healthCheckUrl);
            check.put("Interval", healthCheckInterval);
            check.put("Timeout", healthCheckTimeout);
            if (deregisterCriticalServiceAfter != null && !deregisterCriticalServiceAfter.isBlank()) {
                check.put("DeregisterCriticalServiceAfter", deregisterCriticalServiceAfter);
            }
        }
        return new ConsulAgentClient.ServiceRegistration(
                serviceId, instanceId, host, port, false,
                tags != null ? tags : List.of(),
                metadata != null ? metadata : Map.of(),
                check
        );
    }

    @Override
    public String toString() {
        return "ConsulRegistration{" +
                "serviceId='" + serviceId + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", secure=" + secure +
                ", tags=" + tags +
                ", metadata=" + metadata +
                '}';
    }
}