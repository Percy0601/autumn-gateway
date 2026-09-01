package xyz.wewin.autumn.gateway.examples.httpexchange.provider.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import xyz.wewin.autumn.gateway.examples.httpexchange.UserClient;

/**
 * 整合 HttpServiceProxyFactory + LoadBalancer + 自定义服务发现：
 * 用 {@code @LoadBalanced WebClient.Builder} 构建 {@code lb://httpexchage-provider}
 * 的 WebClient，再通过 {@link HttpServiceProxyFactory} 生成 {@link UserClient} 远程代理，
 * 每次调用由 LoadBalancer（默认 RoundRobin）在 httpexchage-provider 的多个实例间分发。
 */
@Configuration
public class HttpExchangeConfig {

    /**
     * 关键：@LoadBalanced 让 WebClient 能解析 lb:// 前缀（由 LoadBalancer 解析实例）
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder lbWebClientBuilder() {
        return WebClient.builder();
    }

    /**
     * @Primary：本类自身也实现了 {@link UserClient}（UserApiController），
     * 这里声明的远程代理优先，避免按类型注入时产生歧义。
     */
    @Bean
    @Primary
    public UserClient userClient(WebClient.Builder lbWebClientBuilder) {
        // lb://httpexchage-provider 为服务名前缀，配合 UserClient 上的 @HttpExchange("/users")
        // 实际请求为 lb://httpexchage-provider/users/{id}，由 LoadBalancer 选实例
        WebClient wc = lbWebClientBuilder.baseUrl("lb://httpexchage-provider").build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(wc))
                .build();
        return factory.createClient(UserClient.class);
    }
}