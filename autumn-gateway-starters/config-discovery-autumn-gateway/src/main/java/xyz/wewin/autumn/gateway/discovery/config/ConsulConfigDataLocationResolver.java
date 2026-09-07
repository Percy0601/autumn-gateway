package xyz.wewin.autumn.gateway.discovery.config;

import java.util.List;

import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;

/**
 * 让 Spring Boot 识别 {@code spring.config.import=consul:}。
 *
 * <p>连接参数统一从顶层前缀 {@code consul.*} 绑定到 {@link ConsulProperties}；
 * config 行为参数从 {@code consul.config.*} 绑定到 {@link ConsulConfigProperties}。</p>
 */
public class ConsulConfigDataLocationResolver
        implements ConfigDataLocationResolver<ConsulConfigDataResource> {

    public static final String PREFIX = "consul";

    @Override
    public boolean isResolvable(ConfigDataLocationResolverContext context,
                                ConfigDataLocation location) {
        return location.hasPrefix(PREFIX);
    }

    @Override
    public List<ConsulConfigDataResource> resolve(ConfigDataLocationResolverContext context,
                                                  ConfigDataLocation location) {
        Binder binder = context.getBinder();

        ConsulProperties consulProperties = binder
                .bind(ConsulProperties.PREFIX, Bindable.of(ConsulProperties.class))
                .orElseGet(ConsulProperties::new);

        ConsulConfigProperties configProperties = binder
                .bind(ConsulConfigProperties.PREFIX, Bindable.of(ConsulConfigProperties.class))
                .orElseGet(ConsulConfigProperties::new);

        if (configProperties.getApplication() == null || configProperties.getApplication().isBlank()) {
            configProperties.setApplication(binder
                    .bind("spring.application.name", Bindable.of(String.class))
                    .orElse("application"));
        }

        return List.of(new ConsulConfigDataResource(consulProperties, configProperties));
    }
}