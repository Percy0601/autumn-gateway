package xyz.wewin.autumn.gateway.discovery.starter.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;

import xyz.wewin.autumn.gateway.discovery.config.ConsulConfigProperties;
import xyz.wewin.autumn.gateway.discovery.config.ConsulConfigWatchRefresher;

/**
 * Consul 配置中心自动配置。
 *
 * <p>说明：{@code spring.config.import=consul:} 的解析/加载（ConfigData）由框架在
 * 环境准备阶段完成，与 Spring 容器无关，因此本自动配置只负责「运行期自动刷新」；
 * ConfigData resolver/loader 通过 core 模块中的 imports 文件注册。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ConsulConfigProperties.class)
@ConditionalOnProperty(prefix = ConsulConfigProperties.PREFIX, name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ConsulConfigAutoConfiguration {

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = ConsulConfigProperties.PREFIX, name = "watch-enabled",
            havingValue = "true", matchIfMissing = true)
    public ConsulConfigWatchRefresher consulConfigWatchRefresher(
            ConfigurableEnvironment environment,
            ConsulConfigProperties properties,
            ApplicationEventPublisher publisher) {
        ConsulConfigWatchRefresher refresher =
                new ConsulConfigWatchRefresher(environment, properties, publisher);
        refresher.start();
        return refresher;
    }
}
