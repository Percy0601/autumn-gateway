package xyz.wewin.autumn.gateway.discovery.config;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;

import xyz.wewin.autumn.gateway.discovery.consul.ConsulKvClient;
import xyz.wewin.autumn.gateway.discovery.consul.ConsulKvClient.ConsulKvEntry;

/**
 * 配置变更自动刷新器（运行期）。
 *
 * <p>通过 Consul Blocking Query（长轮询）监听 KV 变化。与旧版"单线程定时任务
 * 串行长轮询多个 context"不同，这里为每个 context 起一个独立守护线程，进入
 * "请求 → 阻塞等待 → 收到响应 → 立即重新请求"的持续循环，响应即重发、无固定间隔：</p>
 * <ul>
 *   <li>配置变化时，阻塞中的请求会立刻返回（近实时，不受 {@code wait} 大小影响）；</li>
 *   <li>{@code wait}（consul.config.watch-delay）只决定"无事发生时空闲唤醒"的频率——
 *       超时无变化时 Consul 返回的是当前全量快照，wait 越大越省流量（服务端上限 10 分钟）；</li>
 *   <li>整个进程复用同一个 {@link ConsulKvClient} / HTTP 连接池，不再每个周期新建连接；</li>
 *   <li>对返回快照做"新增 / 修改 / 删除"三级 diff，仅真实变化才替换同名 PropertySource
 *       并发布 {@link EnvironmentChangeEvent}（事件 keys 只含实际变化的 key，删除的 key 也会上报）；</li>
 *   <li>连接异常时退避重试，避免 Consul 不可用造成忙轮询。</li>
 * </ul>
 */
public class ConsulConfigWatchRefresher {

    private static final Logger log = LoggerFactory.getLogger(ConsulConfigWatchRefresher.class);

    /** 连接 / 请求异常后的退避重试间隔，避免 Consul 不可用时疯狂重连 */
    private static final long RETRY_BACKOFF_MILLIS = 5_000L;

    private final ConfigurableEnvironment environment;
    private final ConsulProperties consulProperties;
    private final ConsulConfigProperties configProperties;
    private final ApplicationEventPublisher publisher;
    /** 串行化多个 context 对 Environment 的替换与事件发布，避免并发修改 PropertySources */
    private final Object applyLock = new Object();
    /** 每个 context 最近一次已应用的内容快照，用于精确 diff（仅在 applyLock 内访问） */
    private final Map<String, Map<String, String>> snapshots = new HashMap<>();

    private ConsulKvClient client;
    private volatile boolean running;
    private volatile List<Thread> watcherThreads = List.of();

    public ConsulConfigWatchRefresher(ConfigurableEnvironment environment,
                                      ConsulProperties consulProperties,
                                      ConsulConfigProperties configProperties,
                                      ApplicationEventPublisher publisher) {
        this.environment = environment;
        this.consulProperties = consulProperties;
        this.configProperties = configProperties;
        this.publisher = publisher;
    }

    public void start() {
        if (!configProperties.isWatchEnabled() || running) {
            return;
        }
        running = true;
        // 复用同一 HTTP 连接池：长轮询连接的 keep-alive 跨周期生效，避免每周期新建连接
        client = ConsulConfigDataLoader.newClient(consulProperties);
        List<String> roots = ConsulConfigDataLoader.contextRoots(configProperties, environment);
        List<Thread> threads = new ArrayList<>();
        for (String root : roots) {
            Thread thread = new Thread(() -> watchLoop(root), "consul-config-watch-" + root);
            thread.setDaemon(true);
            thread.start();
            threads.add(thread);
            log.info("Consul 配置长轮询已启动: {}（单次最长等待 {}s，变更即时返回）", root, effectiveWaitSeconds());
        }
        watcherThreads = List.copyOf(threads);
    }

    public void stop() {
        running = false;
        for (Thread thread : watcherThreads) {
            thread.interrupt();
        }
    }

    private long effectiveWaitSeconds() {
        return Math.max(1,
                Math.min(configProperties.getWatchDelay().toSeconds(), ConsulKvClient.MAX_WAIT_SECONDS));
    }

    /** 单个 context 的持续阻塞循环：收到响应后立即重新发起长轮询，不做固定间隔调度 */
    private void watchLoop(String root) {
        long index = 0L;
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                ConsulKvClient.WatchResult result =
                        client.watch(root, index, Duration.ofSeconds(effectiveWaitSeconds()));
                long nextIndex = result.index();
                if (nextIndex < index) {
                    // Consul 集群重启 / 数据重建时索引可能回退：按官方建议重置游标重新阻塞
                    log.warn("Consul KV index 回退 {} -> {}（root={}），重置游标", index, nextIndex, root);
                    index = 0L;
                    continue;
                }
                index = Math.max(nextIndex, 1L); // 至少为 1，避免空子树时 index=0 导致忙轮询
                applyIfChanged(root, result.entries());
            } catch (IOException e) {
                if (running) {
                    log.warn("Consul 长轮询失败({}): {}，{}ms 后重试", root, e.getMessage(), RETRY_BACKOFF_MILLIS);
                }
                sleepQuietly();
            } catch (RuntimeException e) {
                if (running) {
                    log.warn("Consul 长轮询异常({}): {}", root, e.toString());
                }
                sleepQuietly();
            }
        }
        log.info("Consul 配置长轮询已停止: {}", root);
    }

    /** 内容 diff：仅在实际新增 / 修改 / 删除时替换 PropertySource 并发布事件（删除的 key 一并上报） */
    private void applyIfChanged(String root, List<ConsulKvEntry> entries) {
        String sourceName = ConsulConfigDataLoader.sourceName(root);
        Map<String, String> next = new HashMap<>();
        for (ConsulKvEntry entry : entries) {
            String key = ConsulConfigDataLoader.relativeKey(root, entry.key());
            if (!key.isBlank()) {
                next.put(key, entry.value());
            }
        }

        Set<String> changed = new LinkedHashSet<>();
        synchronized (applyLock) {
            Map<String, String> previous = snapshots.get(root);
            if (previous == null) {
                previous = loadEnvironmentSnapshot(sourceName);
                snapshots.put(root, previous);
            }
            if (!previous.isEmpty() || !next.isEmpty()) {
                for (String key : previous.keySet()) {
                    if (!next.containsKey(key)) {
                        changed.add(key); // 删除
                    }
                }
                for (Map.Entry<String, String> e : next.entrySet()) {
                    String oldValue = previous.get(e.getKey());
                    if (oldValue == null || !oldValue.equals(e.getValue())) {
                        changed.add(e.getKey()); // 新增 / 修改
                    }
                }
            }
            if (changed.isEmpty()) {
                return; // 长轮询超时返回的全量快照无变化
            }
            PropertiesPropertySource fresh = new PropertiesPropertySource(sourceName, toProperties(next));
            if (environment.getPropertySources().contains(sourceName)) {
                environment.getPropertySources().replace(sourceName, fresh);
            } else {
                environment.getPropertySources().addLast(fresh);
            }
            snapshots.put(root, next);
        }
        log.info("Consul 配置变更 root={}: {}", root, changed);
        publisher.publishEvent(new EnvironmentChangeEvent(environment, changed));
    }

    /** 启动时环境里可能已由 ConfigData 加载过同名 PropertySource，取其内容作为首个 diff 基线 */
    private Map<String, String> loadEnvironmentSnapshot(String sourceName) {
        PropertySource<?> existing = environment.getPropertySources().get(sourceName);
        Map<String, String> snapshot = new HashMap<>();
        if (existing instanceof PropertiesPropertySource pps) {
            pps.getSource().forEach((key, value) -> {
                if (key != null) {
                    snapshot.put(key.toString(), value == null ? null : value.toString());
                }
            });
        }
        return snapshot;
    }

    private static Properties toProperties(Map<String, String> values) {
        Properties properties = new Properties();
        properties.putAll(values);
        return properties;
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
