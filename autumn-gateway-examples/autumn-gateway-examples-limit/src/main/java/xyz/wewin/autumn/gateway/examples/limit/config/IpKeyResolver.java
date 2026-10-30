package xyz.wewin.autumn.gateway.examples.limit.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 按客户端真实 IP 限流：优先取 CommonFilter 写入的 X-Real-IP，回退 remote address（null-safe）。
 */
@Component("ipKeyResolver")
public class IpKeyResolver implements KeyResolver {
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (ip == null || ip.isBlank()) {
            var addr = exchange.getRequest().getRemoteAddress();
            ip = addr == null ? "unknown" : addr.getHostString();
        }
        return Mono.just(ip);
    }
}
