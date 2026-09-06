package xyz.wewin.autumn.gateway.discovery.config;

import java.util.Objects;

import org.springframework.boot.context.config.ConfigDataResource;

/**
 * 单个 Consul 配置数据源定位（对应一次 {@code spring.config.import=consul:}）。
 * 同一资源可能被加载多次（多 profile / 多次 import），因此需要实现 equals/hashCode。
 */
public class ConsulConfigDataResource extends ConfigDataResource {

    private final ConsulConfigProperties properties;

    public ConsulConfigDataResource(ConsulConfigProperties properties) {
        this.properties = properties;
    }

    public ConsulConfigProperties getProperties() {
        return properties;
    }

    /** 唯一标识：host:port/prefix/application */
    public String identity() {
        return properties.getHost() + ":" + properties.getPort()
                + "/" + properties.getPrefix() + "/" + properties.getApplication();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConsulConfigDataResource that)) {
            return false;
        }
        return Objects.equals(identity(), that.identity());
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity());
    }

    @Override
    public String toString() {
        return "consul-config[" + identity() + "]";
    }
}
