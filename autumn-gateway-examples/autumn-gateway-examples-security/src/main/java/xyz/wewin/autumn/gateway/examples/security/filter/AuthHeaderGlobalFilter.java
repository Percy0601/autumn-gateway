package xyz.wewin.autumn.gateway.examples.security.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthHeaderGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 从 Reactive Security Context 中获取 Jwt 对象
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> {
                    Object principal = securityContext.getAuthentication().getPrincipal();
                    if (principal instanceof Jwt jwt) {
                        // 从 JWT 中提取 Claims
                        String userId = jwt.getClaimAsString("user_id");
                        String username = jwt.getClaimAsString("user_name");

                        // 构建新的 Request，加入自定义 Header
                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("X-User-Id", userId)
                                .header("X-Username", username)
                                .build();

                        // 将新的 Request 放回 Exchange
                        return exchange.mutate().request(mutatedRequest).build();
                    }
                    return exchange;
                })
                .defaultIfEmpty(exchange) // 如果没拿到认证信息，原样放行（会被 Security 拦截）
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        // 设置优先级，确保在 Security 鉴权之后执行
        return -100;
    }
}
