package xyz.wewin.autumn.gateway.discovery.consul;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.wewin.autumn.gateway.discovery.config.ConsulDiscoveryProperties;

public class ConsulDiscoveryWatcher {

    private static final Logger log = LoggerFactory.getLogger(ConsulDiscoveryWatcher.class);

    private final ConsulAgentClient client;
    private final ConsulDiscoveryProperties properties;

    private volatile List<String> cachedServices = List.of();
    private final Map<String, List<ConsulAgentClient.ServiceNode>> cachedInstances = new ConcurrentHashMap<>();
    private final Map<String, Long> serviceIndexes = new ConcurrentHashMap<>();
    private final Map<String, Long> instanceIndexes = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private volatile boolean running;

    public ConsulDiscoveryWatcher(ConsulAgentClient client, ConsulDiscoveryProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public List<String> getCachedServices() {
        return cachedServices;
    }

    public List<ConsulAgentClient.ServiceNode> getCachedInstances(String serviceId) {
        return cachedInstances.getOrDefault(serviceId, Collections.emptyList());
    }

    public void start() {
        if (!properties.isWatchEnabled() || running) {
            return;
        }
        running = true;
        ThreadFactory threadFactory = runnable -> {
            Thread t = new Thread(runnable, "consul-discovery-watch");
            t.setDaemon(true);
            return t;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
        long period = Math.max(1, properties.getWatchDelay().toSeconds());
        scheduler.scheduleWithFixedDelay(this::pollOnce, 1, period, TimeUnit.SECONDS);
        log.info("Consul Discovery watcher started, polling every {}s", period);
    }

    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void pollOnce() {
        if (!running) return;
        try {
            // TODO 优化：只轮询关注的服务
            pollServices();
            // TODO 优化：只轮询关注的服务实例
            pollAllInstances();
        } catch (Exception e) {
            log.warn("Consul Discovery watcher poll error: {}", e.getMessage());
        }
    }

    private void pollServices() throws IOException {
        long index = serviceIndexes.getOrDefault("__services__", 0L);
        ConsulAgentClient.WatchResult result = client.watchServices(index,
                Duration.ofSeconds(Math.min(properties.getWatchDelay().toSeconds(), 55)));
        serviceIndexes.put("__services__", result.index());

        if (!result.services().equals(cachedServices)) {
            cachedServices = List.copyOf(result.services());
            log.info("Consul services changed: {}", cachedServices);
            // 清理不存在的服务的实例缓存
            cachedInstances.keySet().retainAll(cachedServices);
        }
    }

    private void pollAllInstances() {
        for (String service : cachedServices) {
            pollOneService(service);
        }
    }

    private void pollOneService(String serviceId) {
        try {
            long index = instanceIndexes.getOrDefault(serviceId, 0L);
            ConsulAgentClient.WatchResultNodes result = client.watchHealthyInstances(serviceId, index,
                    Duration.ofSeconds(Math.min(properties.getWatchDelay().toSeconds(), 55)));
            instanceIndexes.put(serviceId, result.index());

            cachedInstances.put(serviceId, List.copyOf(result.nodes()));
        } catch (IOException e) {
            log.debug("Poll instances for [{}] failed: {}", serviceId, e.getMessage());
        }
    }
}