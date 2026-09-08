package xyz.wewin.autumn.gateway.examples.httpexchange.provider.loadbalancer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * LoadBalancer 自定义配置，把 {@link AutumnServiceInstanceListSupplier} 挂到每个 lb:// 服务上。
 *
 * <p>装配原理：{@code LoadBalancerClientFactory} 为每个服务创建独立 child context；
 * 默认的 {@code discoveryClientServiceInstanceListSupplier} 带
 * {@code @ConditionalOnMissingBean}，本配置通过
 * {@code @LoadBalancerClients(defaultConfiguration = ...)} 注册进去后，
 * 自定义 supplier 会覆盖默认实现——这是自定义 LoadBalancer 策略的标准姿势。</p>
 *
 * <p>注意：本类<strong>不能</strong>加 {@code @Configuration}，否则会被组件扫描进主上下文，
 * 导致 supplier 在缺 {@code loadbalancer.client.name} 的环境下构造失败。</p>
 *
 * @author: autumn-gateway
 */
public class AutumnLoadBalancerConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceInstanceListSupplier autumnServiceInstanceListSupplier(
            DiscoveryClient discoveryClient, Environment environment) {
        return new AutumnServiceInstanceListSupplier(discoveryClient, environment);
    }
}
