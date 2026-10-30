package xyz.wewin.autumn.gateway.examples.limit.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 官方 {@code Bucket4jRateLimiter} 需要的桶存储后端：单机内存，用 Caffeine 本地缓存。
 *
 * <p>Gateway 的 {@code GatewayAutoConfiguration.Bucket4jConfiguration} 在检测到
 * {@link AsyncProxyManager} bean 存在时，会自动装配 {@code Bucket4jRateLimiter}，
 * 不需要自己 new，也不需要再声明一个 {@code RateLimiter} bean。
 *
 * <p>key = {@code KeyResolver} 解析出的限流维度（本例为客户端 IP）。
 * maximumSize 限制桶数量（每个 key 一个桶），避免被可控的 key 空间撑爆内存。
 */
@Configuration
public class Bucket4jConfig {

    @Bean
    public AsyncProxyManager<String> caffeineProxyManager() {
        Caffeine<String, RemoteBucketState> builder =
                (Caffeine) Caffeine.newBuilder().maximumSize(10_000);
        return new CaffeineProxyManager<>(builder, Duration.ofMinutes(1)).asAsync();
    }
}
