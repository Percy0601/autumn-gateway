package xyz.wewin.autumn.gateway.examples.oidc.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局过滤器：将认证信息注入到下游请求 Header 中
 * <p>
 * 支持两种认证模式：
 * <ul>
 *   <li>JWT Resource Server：principal 为 Jwt，提取 user_id / user_name</li>
 *   <li>OAuth2 Login：principal 为 OAuth2User（如 WeChatOAuth2User），提取 openid / nickname</li>
 * </ul>
 */
@Component
public class AuthHeaderGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> {
                    Object principal = securityContext.getAuthentication().getPrincipal();

                    ServerHttpRequest mutatedRequest;

                    if (principal instanceof Jwt jwt) {
                        // JWT Resource Server 模式（下游服务通过 Bearer Token 认证）
                        String userId = jwt.getClaimAsString("user_id");
                        String username = jwt.getClaimAsString("user_name");

                        mutatedRequest = exchange.getRequest().mutate()
                                .header("X-User-Id", userId != null ? userId : "")
                                .header("X-Username", username != null ? username : "")
                                .header("X-Auth-Type", "JWT")
                                .build();

                    } else if (principal instanceof OAuth2User oauth2User) {
                        // OAuth2 Login 模式（网关完成 OAuth2 登录后）
                        String userId = oauth2User.getName(); // openid for WeChat
                        String username = getAttribute(oauth2User, "nickname", "name", "login");

                        mutatedRequest = exchange.getRequest().mutate()
                                .header("X-User-Id", userId != null ? userId : "")
                                .header("X-Username", username != null ? username : "")
                                .header("X-Auth-Type", "OAUTH2")
                                .build();

                    } else {
                        return exchange;
                    }

                    return exchange.mutate().request(mutatedRequest).build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    /**
     * 按优先级从 OAuth2User 属性中获取值（取第一个非空值）
     */
    private String getAttribute(OAuth2User user, String... keys) {
        for (String key : keys) {
            Object val = user.getAttribute(key);
            if (val != null) {
                return val.toString();
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
