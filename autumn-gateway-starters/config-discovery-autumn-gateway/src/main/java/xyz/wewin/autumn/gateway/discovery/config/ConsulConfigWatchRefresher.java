package xyz.wewin.autumn.gateway.discovery.config;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import xyz.wewin.autumn.gateway.discovery.consul.ConsulKvClient;
import xyz.wewin.autumn.gateway.discovery.consul.ConsulKvClient.ConsulKvEntry;

/**
 * 配置变更自动刷新器（运行期）。
 *
 * <p>通过 Consul Blocking Query（长轮询）监听 KV 变化：单线程后台定时任务对每个
 * context 发出带 {@code index/wait} 的请求，Consul 端配置变化时立即返回；
 * 检测到变化后：</p>
 * <ol>
 *   <li>重建同名 PropertySource 并替换进 {@link ConfigurableEnvironment}；</li>
 *   <li>发布 {@link EnvironmentChangeEvent}，使标注了 {@code @RefreshScope} 的 Bean
 *       （@Value / @ConfigurationProperties）自动重建并读取最新值；</li>
 *   <li>业务侧（如网关路由）也可监听同一事件做自定义刷新。</li>
 * </ol>
 */
public class ConsulConfigWatchRefresher {

    private static final Logger log = LoggerFactory.getLogger(ConsulConfigWatchRefresher.class);

    private final ConfigurableEnvironment environment;
    private final ConsulConfigProperties properties;
    private final ApplicationEventPublisher publisher;
    private final Map<String, Long> indexes = new LinkedHashMap<>();

    private ScheduledExecutorService scheduler;
    private volatile boolean running;

    public ConsulConfigWatchRefresher(ConfigurableEnvironment environment,
                                      ConsulConfigProperties properties,
                                      ApplicationEventPublisher publisher) {
        this.environment = environment;
        this.properties = properties;
        this.publisher = publisher;
    }

    public void start() {
        if (!properties.isWatchEnabled() || running) {
            return;
        }
        running = true;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "consul-config-watch");
            thread.setDaemon(true);
            return thread;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
        long period = Math.max(1, properties.getWatchDelay().toSeconds());
        scheduler.scheduleWithFixedDelay(this::pollOnce, 1, period, TimeUnit.SECONDS);
        log.info("Consul 配置自动刷新已启动, 周期={}s", period);
    }

    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void pollOnce() {
        if (!running) {
            return;
        }
        try {
            ConsulKvClient client = ConsulConfigDataLoader.newClient(properties);
            List<String> roots = ConsulConfigDataLoader.contextRoots(properties, environment);
            Set<String> changedKeys = new LinkedHashSet<>();
            boolean changed = false;
            for (String root : roots) {
                changed |= pollRoot(client, root, changedKeys);
            }
            if (changed) {
                log.info("Consul 配置变更: {}", changedKeys);
                publisher.publishEvent(new EnvironmentChangeEvent(environment, changedKeys));
            }
        } catch (Exception e) {
            log.warn("Consul 配置刷新轮询异常: {}", e.getMessage());
        }
    }

    /** 单个 context 长轮询；配置有变化时替换同名 PropertySource，并记录变化的 key */
    private boolean pollRoot(ConsulKvClient client, String root, Set<String> changedKeys) throws IOException {
        long index = indexes.getOrDefault(root, 0L);
        ConsulKvClient.WatchResult result = client.watch(root, index,
                Duration.ofSeconds(Math.min(properties.getWatchDelay().toSeconds(), 55)));
        indexes.put(root, result.index());

        Properties next = new Properties();
        for (ConsulKvEntry entry : result.entries()) {
            String key = ConsulConfigDataLoader.relativeKey(root, entry.key());
            if (!key.isBlank()) {
                next.put(key, entry.value());
            }
        }

        String sourceName = ConsulConfigDataLoader.sourceName(root);
        if (!environment.getPropertySources().contains(sourceName)) {
            if (next.isEmpty()) {
                return false; // context 尚不存在且仍无数据
            }
            environment.getPropertySources().addLast(new PropertiesPropertySource(sourceName, next));
            changedKeys.addAll(next.stringPropertyNames());
            return true;
        }
        PropertiesPropertySource current =
                (PropertiesPropertySource) environment.getPropertySources().get(sourceName);
        if (current.getSource().equals(next)) {
            return false; // 无变化
        }
        environment.getPropertySources().replace(sourceName,
                new PropertiesPropertySource(sourceName, next));
        changedKeys.addAll(next.stringPropertyNames());
        return true;
    }
}
