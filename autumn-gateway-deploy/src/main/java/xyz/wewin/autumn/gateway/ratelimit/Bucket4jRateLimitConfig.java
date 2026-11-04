package xyz.wewin.autumn.gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * 官方 {@code RequestRateLimiter} 的「单机内存」实现所需的两个 bean。
 *
 * <p><b>默认完全不注册</b>：只有 {@code autumn.limit.enabled=true} 时本配置才生效，
 * 因此引入 bucket4j 依赖本身不会改变现有发行形态的任何行为。</p>
 *
 * <h3>为什么不是「默认内存限流」</h3>
 * Gateway 的 {@code RequestRateLimiter} 默认装配的是 {@code RedisRateLimiter}（需要 Redis）。
 * 仅当 classpath 存在 bucket4j 且容器里有 {@link AsyncProxyManager} bean 时，
 * {@code GatewayAutoConfiguration.Bucket4jConfiguration} 才会自动装配内存版
 * {@code Bucket4jRateLimiter}；限流维度由 {@link KeyResolver} 决定。
 * 路由上的配置仍然是 {@code name=RequestRateLimiter}，具体实现由这两个 bean 决定。
 *
 * <h3>IP 取值的可信边界</h3>
 * 默认只取 TCP 远端地址。客户端可随意伪造 {@code X-Forwarded-For} / {@code X-Real-IP}，
 * 直接信任等于「换个头就换一个桶」，限流形同虚设。只有显式配置
 * {@code autumn.limit.trusted-header} 才读取该头，且部署方必须保证边缘代理
 * 会剥离客户端自带的同名头。多段链式值只取第一段。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "autumn.limit", name = "enabled", havingValue = "true")
public class Bucket4jRateLimitConfig {

    /** 桶数量上限：每个 key 一个桶，防止可控 key 空间撑爆内存 */
    @Value("${autumn.limit.cache-maximum-size:10000}")
    private long cacheMaximumSize;

    /** 桶空闲多久后回收 */
    @Value("${autumn.limit.cache-expiration:1m}")
    private Duration cacheExpiration;

    /** 可信的客户端 IP 头；留空表示只信 remoteAddress */
    @Value("${autumn.limit.trusted-header:}")
    private String trustedHeader;

    @Bean
    public AsyncProxyManager<String> caffeineProxyManager() {
        @SuppressWarnings("unchecked")
        Caffeine<String, RemoteBucketState> builder = (Caffeine<String, RemoteBucketState>)
                (Caffeine<?, ?>) Caffeine.newBuilder().maximumSize(this.cacheMaximumSize);
        return new CaffeineProxyManager<>(builder, this.cacheExpiration).asAsync();
    }

    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(resolveClientIp(exchange));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        if (StringUtils.hasText(this.trustedHeader)) {
            String forwarded = exchange.getRequest().getHeaders().getFirst(this.trustedHeader);
            if (StringUtils.hasText(forwarded)) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        if (addr == null || addr.getAddress() == null) {
            return "unknown";
        }
        return addr.getAddress().getHostAddress();
    }
}
