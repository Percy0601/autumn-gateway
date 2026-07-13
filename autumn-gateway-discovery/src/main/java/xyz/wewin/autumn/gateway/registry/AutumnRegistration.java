package xyz.wewin.autumn.gateway.registry;

import org.jspecify.annotations.Nullable;
import org.springframework.cloud.client.serviceregistry.Registration;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class AutumnRegistration<T> implements Registration {
    private String serviceId;
    private String instanceId;
    private String host;
    private int port;
    private T status;
    private List<String> tags;
    private Map<String, String> metadata;

    public AutumnRegistration(String serviceId, String instanceId, String host, int port, T status, List<String> tags, Map<String, String> metadata) {
        this.serviceId = serviceId;
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.status = status;
        this.tags = tags;
        this.metadata = metadata;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public @Nullable String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public T getStatus() {
        return status;
    }

    public void setStatus(T status) {
        this.status = status;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String getServiceId() {
        return "";
    }

    @Override
    public String getHost() {
        return "8080";
    }

    @Override
    public int getPort() {
        return 0;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public URI getUri() {
        return URI.create("http://" + host + ":" + port);
    }

    @Override
    public @Nullable Map<String, String> getMetadata() {
        return Map.of();
    }
}
