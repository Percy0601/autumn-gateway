package xyz.wewin.autumn.gateway.discovery.config;

import java.util.List;

import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;

/**
 * 让 Spring Boot 识别 {@code spring.config.import=consul:}（参考 spring-cloud-consul-config）。
 *
 * <p>注册方式（Boot 4.x 采用 imports 文件，替代已移除的 spring.factories）：
 * <pre>
 *   META-INF/spring/org.springframework.boot.context.config.ConfigDataLocationResolver.imports
 * </pre>
 * 连接参数（consul.config.*）在此时通过 {@link ConfigDataLocationResolverContext#getBinder()}
 * 绑定自应用自身已有的配置文件（application.properties / 环境变量等）。</p>
 */
public class ConsulConfigDataLocationResolver
        implements ConfigDataLocationResolver<ConsulConfigDataResource> {

    /** 与 spring.config.import=consul: 的 URL 前缀对应 */
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
        ConsulConfigProperties properties = binder
                .bind(ConsulConfigProperties.PREFIX, Bindable.of(ConsulConfigProperties.class))
                .orElseGet(ConsulConfigProperties::new);
        if (properties.getApplication() == null || properties.getApplication().isBlank()) {
            // 应用自身属性（此时 Consul 配置尚未加载，读取的是本地 application.*）
            properties.setApplication(binder
                    .bind("spring.application.name", Bindable.of(String.class))
                    .orElse("application"));
        }
        return List.of(new ConsulConfigDataResource(properties));
    }
}
