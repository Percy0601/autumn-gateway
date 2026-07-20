package xyz.wewin.autumn.gateway.examples.httpexchange.consumer.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import xyz.wewin.autumn.gateway.examples.httpexchange.UserClient;

//@Configuration
public class HttpExchangeConfig {

    /**
     * 关键：@LoadBalanced 让 WebClient 能解析 lb:// 前缀
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder lbWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public UserClient userClient(WebClient.Builder lbWebClientBuilder) {
        WebClient wc = lbWebClientBuilder.build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(wc))
                .build();
        return factory.createClient(UserClient.class);
    }
}