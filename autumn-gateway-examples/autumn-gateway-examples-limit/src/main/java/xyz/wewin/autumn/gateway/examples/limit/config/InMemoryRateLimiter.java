package xyz.wewin.autumn.gateway.examples.limit.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component("inMemoryRateLimiter")
public class InMemoryRateLimiter extends AbstractRateLimiter<InMemoryRateLimiter.Config> {
    // ⚠️ 这个常量必须跟 properties 里的前缀对上
    public static final String CONFIGURATION_PROPERTY_NAME = "in-memory-rate-limiter";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Config defaultConfig = new Config(2, 4);

    public InMemoryRateLimiter(ConfigurationService service) {
        super(Config.class, CONFIGURATION_PROPERTY_NAME, service);
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        Config cfg = getConfig().getOrDefault(routeId, defaultConfig);

        Bucket bucket = buckets.computeIfAbsent(id + "@" + routeId, k ->
                Bucket4j.builder()
                        .addLimit(Bandwidth.classic(
                                cfg.getBurstCapacity(),
                                Refill.of(cfg.getReplenishRate(), Duration.ofSeconds(10))
                        ))
                        .build()
        );

        var headers = new HashMap<String, String>();
        var probe = bucket.tryConsumeAndReturnRemaining(1);
        headers.put("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            return Mono.just(new Response(true, headers));
        }
        return Mono.just(new Response(false, headers));
    }

    // 属性名跟 properties 里 kebab-case 自动绑定
    public static class Config {
        private int replenishRate = 1;
        private int burstCapacity = 2;

        public Config() {
        }

        public Config(int replenishRate, int burstCapacity) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
        }

        // getter/setter 必须，不然 properties 注入不进去
        public int getReplenishRate() { return replenishRate; }
        public void setReplenishRate(int r) { this.replenishRate = r; }
        public int getBurstCapacity() { return burstCapacity; }
        public void setBurstCapacity(int b) { this.burstCapacity = b; }
    }
}
