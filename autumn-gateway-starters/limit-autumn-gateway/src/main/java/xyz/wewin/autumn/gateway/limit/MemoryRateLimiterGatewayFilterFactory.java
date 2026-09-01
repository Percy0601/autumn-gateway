package xyz.wewin.autumn.gateway.limit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存（Bucket4j）的限流过滤器工厂。
 *
 * <p>路由配置示例（Gateway 5.x 前缀）：
 * <pre>
 * spring.cloud.gateway.server.webflux.routes[0].filters[0].name=MemoryRateLimiter
 * spring.cloud.gateway.server.webflux.routes[0].filters[0].args.replenish-rate=2
 * spring.cloud.gateway.server.webflux.routes[0].filters[0].args.burst-capacity=4
 * spring.cloud.gateway.server.webflux.routes[0].filters[0].args.time-window-seconds=10
 * </pre>
 * 也支持短路配置：{@code MemoryRateLimiter=2,4,10}（顺序见 {@link #shortcutFieldOrder()}）。
 *
 * <p>限流 key 默认取客户端真实 IP：优先读 {@code X-Real-IP}（由 {@link CommonFilter} 写入，
 * 参考 autumn-gateway-examples-limit 的 CommonFilter），取不到时回退 remote address。
 */
public class MemoryRateLimiterGatewayFilterFactory
        extends AbstractGatewayFilterFactory<MemoryRateLimiterGatewayFilterFactory.Config> {
    private Logger log = LoggerFactory.getLogger(getClass());
    /** 参考 CommonFilter：真实客户端 IP 会被写入该 header，默认按它限流 */
    public static final String REAL_IP_HEADER = "X-Real-IP";

    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String UNKNOWN_KEY = "unknown";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Config defaultConfig;

    public MemoryRateLimiterGatewayFilterFactory() {
        this(new Config());
    }

    public MemoryRateLimiterGatewayFilterFactory(Config defaultConfig) {
        super(Config.class);
        this.defaultConfig = defaultConfig == null ? new Config() : defaultConfig;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("replenishRate", "burstCapacity", "timeWindowSeconds");
    }

    @Override
    public GatewayFilter apply(Config config) {
        Config cfg = config == null ? defaultConfig : config;
        return (exchange, chain) -> {
            String key = resolveKey(exchange, cfg);
            Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(cfg));
            var probe = bucket.tryConsumeAndReturnRemaining(1);
            exchange.getResponse().getHeaders()
                    .add(RATE_LIMIT_REMAINING_HEADER, String.valueOf(probe.getRemainingTokens()));
            if (!probe.isConsumed()) {
                log.warn("rate limit exceeded, key: {}", key);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }

    private Bucket newBucket(Config cfg) {
        return Bucket4j.builder()
                .addLimit(Bandwidth.classic(
                        cfg.getBurstCapacity(),
                        Refill.of(cfg.getReplenishRate(), Duration.ofSeconds(cfg.getTimeWindowSeconds()))
                ))
                .build();
    }

    private String resolveKey(ServerWebExchange exchange, Config cfg) {
        ServerHttpRequest request = exchange.getRequest();
        String key = switch (cfg.getKeyType()) {
            case PATH -> request.getURI().getPath();
            case HEADER -> request.getHeaders().getFirst(cfg.getHeaderName());
            case IP -> {
                String realIp = request.getHeaders().getFirst(REAL_IP_HEADER);
                yield (realIp == null || realIp.isBlank())
                        ? (request.getRemoteAddress() == null ? UNKNOWN_KEY : request.getRemoteAddress().getHostString())
                        : realIp;
            }
        };
        return key == null || key.isBlank() ? UNKNOWN_KEY : key;
    }

    public Config getDefaultConfig() {
        return defaultConfig;
    }

    /** 限流 key 的维度 */
    public enum KeyType {
        /** 客户端真实 IP（优先 X-Real-IP，回退 remote address） */
        IP,
        /** 指定请求头 */
        HEADER,
        /** 请求路径 */
        PATH
    }

    /** 限流参数，字段名与路由 args 的 kebab-case 自动绑定 */
    public static class Config {

        private KeyType keyType = KeyType.IP;
        private String headerName = REAL_IP_HEADER;
        private int replenishRate = 1;
        private int burstCapacity = 2;
        private long timeWindowSeconds = 10;

        public Config() {
        }

        public KeyType getKeyType() {
            return keyType;
        }

        public void setKeyType(KeyType keyType) {
            this.keyType = keyType;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public int getReplenishRate() {
            return replenishRate;
        }

        public void setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }

        public long getTimeWindowSeconds() {
            return timeWindowSeconds;
        }

        public void setTimeWindowSeconds(long timeWindowSeconds) {
            this.timeWindowSeconds = timeWindowSeconds;
        }
    }
}
