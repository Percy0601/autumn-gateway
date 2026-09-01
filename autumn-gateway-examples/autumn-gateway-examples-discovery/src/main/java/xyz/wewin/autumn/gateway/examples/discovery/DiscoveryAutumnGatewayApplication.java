package xyz.wewin.autumn.gateway.examples.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.ComponentScan;
import xyz.wewin.autumn.gateway.examples.discovery.loadbalancer.AutumnLoadBalancerConfiguration;

/**
 * 示例：服务发现 + LoadBalancer 整合。
 *
 * <p>{@link LoadBalancerClients} 把 {@link AutumnLoadBalancerConfiguration} 注册为
 * 所有 lb:// 服务的默认 LoadBalancer 配置，挂载自定义的
 * {@code AutumnServiceInstanceListSupplier}（模拟 Consul 风格的健康过滤）。</p>
 */
@LoadBalancerClients(defaultConfiguration = AutumnLoadBalancerConfiguration.class)
@ComponentScan("xyz.wewin.autumn.gateway")
@SpringBootApplication
public class DiscoveryAutumnGatewayApplication {

	static void main(String[] args) {
		SpringApplication.run(DiscoveryAutumnGatewayApplication.class, args);
	}


}
