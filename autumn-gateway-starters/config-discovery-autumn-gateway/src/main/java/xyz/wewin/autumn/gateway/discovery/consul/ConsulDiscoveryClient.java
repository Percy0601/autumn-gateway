package xyz.wewin.autumn.gateway.discovery.consul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConsulDiscoveryClient implements DiscoveryClient {

    public static final String DESCRIPTION = "Consul Discovery Client (Minimal)";

    private static final Logger log = LoggerFactory.getLogger(ConsulDiscoveryClient.class);

    private final ConsulAgentClient client;
    private final ConsulDiscoveryWatcher watcher;

    public ConsulDiscoveryClient(ConsulAgentClient client, ConsulDiscoveryWatcher watcher) {
        this.client = client;
        this.watcher = watcher;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        List<ConsulAgentClient.ServiceNode> nodes;
        if (watcher != null) {
            nodes = watcher.getCachedInstances(serviceId);
            if (nodes != null && !nodes.isEmpty()) {
                return convert(nodes);
            }
        }
        try {
            nodes = client.getHealthyInstances(serviceId);
            return convert(nodes);
        } catch (IOException e) {
            log.warn("Failed to get instances for service [{}]: {}", serviceId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<String> getServices() {
        if (watcher != null) {
            List<String> cached = watcher.getCachedServices();
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
        }
        try {
            return client.getServices();
        } catch (IOException e) {
            log.warn("Failed to get services: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private List<ServiceInstance> convert(List<ConsulAgentClient.ServiceNode> nodes) {
        List<ServiceInstance> instances = new ArrayList<>(nodes.size());
        for (ConsulAgentClient.ServiceNode node : nodes) {
            instances.add(new ConsulServiceInstance(node));
        }
        return instances;
    }
}