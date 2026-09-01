package xyz.wewin.autumn.gateway.examples.discovery.loadbalancer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * LoadBalancer 的自定义配置，把 {@link AutumnServiceInstanceListSupplier} 挂到每个 lb:// 服务上。
 *
 * <p>装配原理（对照 spring-cloud-loadbalancer 源码）：</p>
 * <ul>
 *     <li>{@code LoadBalancerClientFactory} 为每个服务（lb://xxx）创建独立 child context，加载默认配置
 *         {@code LoadBalancerClientConfiguration}；</li>
 *     <li>默认的 {@code discoveryClientServiceInstanceListSupplier} bean 带
 *         {@code @ConditionalOnBean(DiscoveryClient.class)} + {@code @ConditionalOnMissingBean}——
 *         只要容器里有 DiscoveryClient（我们的 {@code AutumnDiscoveryClient}）且没有自定义 supplier，
 *         就用默认桥接；</li>
 *     <li>本类通过 {@code @LoadBalancerClients(defaultConfiguration = ...)} 注册进每个服务的
 *         child context，因为默认 supplier 带 {@code @ConditionalOnMissingBean}，这里的自定义 bean
 *         会覆盖默认实现——这就是"自定义 LoadBalancer 策略"的标准姿势。</li>
 * </ul>
 *
 * @author: autumn-gateway
 */
@Configuration(proxyBeanMethods = false)
public class AutumnLoadBalancerConfiguration {

    /**
     * 覆盖默认的 DiscoveryClientServiceInstanceListSupplier，
     * 在 LoadBalancer 选实例前执行 AutumnServiceInstanceListSupplier 的过滤逻辑。
     */
    @Bean
    @ConditionalOnMissingBean
    public ServiceInstanceListSupplier autumnServiceInstanceListSupplier(
            DiscoveryClient discoveryClient, Environment environment) {
        return new AutumnServiceInstanceListSupplier(discoveryClient, environment);
    }
}
