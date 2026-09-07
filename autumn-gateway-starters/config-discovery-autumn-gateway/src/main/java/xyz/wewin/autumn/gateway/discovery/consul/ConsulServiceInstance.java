package xyz.wewin.autumn.gateway.discovery.consul;

import org.jspecify.annotations.Nullable;
import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class ConsulServiceInstance implements ServiceInstance {

    private final String serviceId;
    private final String instanceId;
    private final String host;
    private final int port;
    private final boolean secure;
    private final Map<String, String> metadata;
    private final List<String> tags;
    private final URI uri;

    public ConsulServiceInstance(String serviceId, String instanceId, String host, int port,
                                 boolean secure, Map<String, String> metadata, List<String> tags) {
        this.serviceId = serviceId;
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.secure = secure;
        this.metadata = metadata;
        this.tags = tags;
        this.uri = URI.create((secure ? "https" : "http") + "://" + host + ":" + port);
    }

    public ConsulServiceInstance(ConsulAgentClient.ServiceNode node) {
        this(node.serviceId(), node.instanceId(), node.host(), node.port(),
                node.secure(), node.metadata(), node.tags());
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
    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public List<String> getTags() {
        return tags;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return "ConsulServiceInstance{" +
                "serviceId='" + serviceId + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", secure=" + secure +
                ", metadata=" + metadata +
                ", tags=" + tags +
                '}';
    }
}