package xyz.wewin.autumn.gateway.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.context.annotation.Bean;
import xyz.wewin.autumn.gateway.discovery.AutumnDiscoveryClient;
import xyz.wewin.autumn.gateway.registry.AutumnRegistryClient;

/**
 * 服务发现自动配置，对应 Spring Cloud Consul 的 {@code ConsulDiscoveryClientConfiguration}。
 *
 * <p>把 {@link AutumnDiscoveryClient} 注册成 Spring Cloud 的 {@code DiscoveryClient} Bean，
 * 网关就能自动感知：lb://xxx 路由、DiscoveryClientRouteDefinitionLocator 动态路由、
 * 以及 LoadBalancer 都依赖它。</p>
 *
 * <p>可通过 {@code spring.cloud.autumn-registry.discovery.enabled=false} 关闭。</p>
 *
 * @author: autumn-gateway
 */
@AutoConfiguration
@ConditionalOnDiscoveryEnabled
@ConditionalOnProperty(value = "spring.cloud.autumn-registry.discovery.enabled", matchIfMissing = true)
public class AutumnDiscoveryClientConfiguration {

    @Bean
    public AutumnDiscoveryClient autumnDiscoveryClient(AutumnRegistryClient autumnRegistryClient) {
        return new AutumnDiscoveryClient(autumnRegistryClient);
    }
}
