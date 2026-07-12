package xyz.wewin.autumn.gateway.examples.consul.config.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
public class RateLimitConfig {

    // 按用户 ID 限流（header X-User-Id），没有则按 IP
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(
                Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-User-Id"))
                        .orElseGet(() -> exchange.getRequest().getRemoteAddress().getAddress().getHostAddress())
        );
    }

    // 显式声明 InMemoryRateLimiter Bean，名称固定为 inMemoryRateLimiter
    @Bean
    public InMemoryRateLimiter inMemoryRateLimiter() {
        // 配置会从 properties 中读取，这里返回空构造即可
        return new InMemoryRateLimiter();
    }
}
