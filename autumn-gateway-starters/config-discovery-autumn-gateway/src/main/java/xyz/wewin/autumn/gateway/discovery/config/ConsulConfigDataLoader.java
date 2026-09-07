package xyz.wewin.autumn.gateway.discovery.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;

import xyz.wewin.autumn.gateway.discovery.consul.ConsulKvClient;
import xyz.wewin.autumn.gateway.discovery.consul.ConsulKvClient.ConsulKvEntry;

public class ConsulConfigDataLoader implements ConfigDataLoader<ConsulConfigDataResource> {

    private static final Logger log = LoggerFactory.getLogger(ConsulConfigDataLoader.class);

    static final String SOURCE_PREFIX = "consul-config";

    @Override
    public ConfigData load(ConfigDataLoaderContext context, ConsulConfigDataResource resource) {
        ConsulConfigProperties configProperties = resource.getProperties();
        ConsulProperties consulProperties = resource.getConsulProperties();
        ConsulKvClient client = newClient(consulProperties);

        List<org.springframework.core.env.PropertySource<?>> sources = new ArrayList<>();
        for (String root : contextRoots(configProperties, null)) {
            Properties values;
            try {
                values = fetch(client, root);
            } catch (IOException e) {
                if (configProperties.isFailFast()) {
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

    private Properties fetch(ConsulKvClient client, String root) throws IOException {
        Properties values = new Properties();
        for (ConsulKvEntry entry : client.list(root)) {
            String key = relativeKey(root, entry.key());
            if (!key.isBlank()) {
                values.put(key, entry.value());
            }
        }
        return values;
    }

    static String relativeKey(String root, String kvKey) {
        return kvKey.startsWith(root + "/") ? kvKey.substring(root.length() + 1) : kvKey;
    }

    static ConsulKvClient newClient(ConsulProperties p) {
        return new ConsulKvClient(p.getScheme(), p.getHost(), p.getPort(), p.getToken(), p.getTimeout());
    }

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

    static String sourceName(String root) { return SOURCE_PREFIX + ":" + root; }
}