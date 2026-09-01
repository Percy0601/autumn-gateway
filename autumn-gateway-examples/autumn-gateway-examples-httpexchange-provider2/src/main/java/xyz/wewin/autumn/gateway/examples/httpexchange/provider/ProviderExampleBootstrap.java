package xyz.wewin.autumn.gateway.examples.httpexchange.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import xyz.wewin.autumn.gateway.examples.httpexchange.provider.loadbalancer.AutumnLoadBalancerConfiguration;

/**
 * provider2：既是被调用方（注册为 httpexchage-provider 的第二个实例，端口 8085），
 * 又是调用方——整合 自定义服务发现 + LoadBalancer + HttpServiceProxyFactory，
 * 通过 {@code @LoadBalanced WebClient.Builder} 以 lb://httpexchage-provider 发起调用。
 *
 * <p>{@link LoadBalancerClients} 把 {@link AutumnLoadBalancerConfiguration} 注册为
 * 所有 lb:// 服务的默认 LoadBalancer 配置，挂载自定义的
 * {@code AutumnServiceInstanceListSupplier}（健康过滤 + 静态实例兜底）。</p>
 */
@LoadBalancerClients(defaultConfiguration = AutumnLoadBalancerConfiguration.class)
@SpringBootApplication
public class ProviderExampleBootstrap {

	static void main(String[] args) {
		SpringApplication.run(ProviderExampleBootstrap.class, args);
	}


}
