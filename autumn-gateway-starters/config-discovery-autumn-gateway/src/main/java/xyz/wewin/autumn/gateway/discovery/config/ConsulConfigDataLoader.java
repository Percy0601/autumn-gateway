package xyz.wewin.autumn.gateway.discovery.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;

import xyz.wewin.autumn.gateway.discovery.consul.ConsulKvClient;
import xyz.wewin.autumn.gateway.discovery.consul.ConsulKvClient.ConsulKvEntry;

/**
 * 实际从 Consul KV 拉取配置并转成 PropertySource。
 *
 * <p>加载两个 context（优先级从低到高）：
 * <ol>
 *   <li>{@code {prefix}/application} 全局默认配置</li>
 *   <li>{@code {prefix}/{application}} 当前应用配置（后添加 → 优先级更高，可覆盖默认）</li>
 * </ol>
 * KV key 相对部分的原样作为 property key（如 {@code config/order-service/server.port=8080}
 * → {@code server.port=8080}）。</p>
 */
public class ConsulConfigDataLoader implements ConfigDataLoader<ConsulConfigDataResource> {

    private static final Logger log = LoggerFactory.getLogger(ConsulConfigDataLoader.class);

    /** PropertySource 命名统一前缀，运行期刷新按该名字定位替换 */
    static final String SOURCE_PREFIX = "consul-config";

    @Override
    public ConfigData load(ConfigDataLoaderContext context, ConsulConfigDataResource resource) {
        ConsulConfigProperties properties = resource.getProperties();
        ConsulKvClient client = newClient(properties);
        List<org.springframework.core.env.PropertySource<?>> sources = new ArrayList<>();
        for (String root : contextRoots(properties, null)) {
            Properties values;
            try {
                values = fetch(client, root, properties);
            } catch (IOException e) {
                if (properties.isFailFast()) {
                    throw new IllegalStateException("加载 Consul 配置失败: " + root, e);
                }
                log.warn("跳过 Consul 配置（fail-fast=false）: {} - {}", root, e.getMessage());
                values = new Properties();
            }
            sources.add(new PropertiesPropertySource(sourceName(root), values));
            log.info("Consul 配置已加载: {} ({} 项)", root, values.size());
        }
        return new ConfigData(sources);
    }

    private Properties fetch(ConsulKvClient client, String root, ConsulConfigProperties properties)
            throws IOException {
        Properties values = new Properties();
        for (ConsulKvEntry entry : client.list(root)) {
            String key = relativeKey(root, entry.key());
            if (!key.isBlank()) {
                values.put(key, entry.value());
            }
        }
        return values;
    }

    /** 去掉 KV 前缀，得到 property key */
    static String relativeKey(String root, String kvKey) {
        return kvKey.startsWith(root + "/") ? kvKey.substring(root.length() + 1) : kvKey;
    }

    static ConsulKvClient newClient(ConsulConfigProperties properties) {
        return new ConsulKvClient(properties.getScheme(), properties.getHost(),
                properties.getPort(), properties.getToken(), properties.getTimeout());
    }

    /** 需要加载的 context 根（含 prefix）。env 为空时仅用于 ConfigData 阶段取不到 application 的场景兜底 */
    static List<String> contextRoots(ConsulConfigProperties properties, Environment env) {
        String prefix = properties.getPrefix() == null || properties.getPrefix().isBlank()
                ? "config" : properties.getPrefix();
        String application = properties.getApplication();
        if (application == null && env != null) {
            application = env.getProperty("spring.application.name", "application");
        }
        List<String> roots = new ArrayList<>();
        roots.add(prefix + "/application");
        roots.add(prefix + "/" + (application == null ? "application" : application));
        return roots;
    }

    static String sourceName(String root) {
        return SOURCE_PREFIX + ":" + root;
    }
}
