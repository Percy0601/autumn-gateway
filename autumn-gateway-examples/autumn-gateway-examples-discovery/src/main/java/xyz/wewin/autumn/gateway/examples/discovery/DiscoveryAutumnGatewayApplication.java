package xyz.wewin.autumn.gateway.examples.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import xyz.wewin.autumn.gateway.examples.discovery.loadbalancer.AutumnLoadBalancerConfiguration;

/**
 * 示例：服务发现 + LoadBalancer 整合。
 *
 * <p>{@link LoadBalancerClients} 把 {@link AutumnLoadBalancerConfiguration} 注册为
 * 所有 lb:// 服务的默认 LoadBalancer 配置，挂载自定义的
 * {@code AutumnServiceInstanceListSupplier}（模拟 Consul 风格的健康过滤）。</p>
 *
 * <p>注意：不要用 @ComponentScan 扫整个 xyz.wewin.autumn.gateway——
 * defaultConfiguration 类绝不能被主上下文组件扫描到（会在根上下文创建缺 serviceId 的
 * supplier bean 导致启动失败），autumn-gateway-discovery 通过 AutoConfiguration.imports
 * 自动装配，无需手动扫描。</p>
 */
@LoadBalancerClients(defaultConfiguration = AutumnLoadBalancerConfiguration.class)
@SpringBootApplication
public class DiscoveryAutumnGatewayApplication {

	static void main(String[] args) {
		SpringApplication.run(DiscoveryAutumnGatewayApplication.class, args);
	}


}
