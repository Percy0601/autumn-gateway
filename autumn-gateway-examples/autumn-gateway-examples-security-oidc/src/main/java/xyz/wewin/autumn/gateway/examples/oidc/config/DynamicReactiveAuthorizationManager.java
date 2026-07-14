package xyz.wewin.autumn.gateway.examples.oidc.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import reactor.core.publisher.Mono;

/**
 *
 * @author: baoxin.zhao
 * @date: 7/4/26
 */

@Component
public class DynamicReactiveAuthorizationManager
        implements ReactiveAuthorizationManager<AuthorizationContext> {
    private Logger log = LoggerFactory.getLogger(this.getClass());
    private final Environment environment;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    public DynamicReactiveAuthorizationManager(Environment environment) {
        this.environment = environment;
    }

    // ========== 1. verify：预钩子，白名单直接 complete，否则 complete（让链继续）==========
    @Override
    public Mono<Void> verify(Mono<Authentication> authentication,
                             AuthorizationContext context) {
        String path = context.getExchange().getRequest().getURI().getPath();

        // 白名单（从 Environment 读 Consul KV，RefreshEvent 后自动更新）
        if (isWhite(path)) {
            return Mono.empty(); // complete → 放行，不进 oauth2ResourceServer
        }
        return Mono.empty(); // 非白名单 → 让链继续到 oauth2ResourceServer(jwt) 验签
    }

    // ========== 2. authorize：JWT 验签后二次判角色（path-role 动态规则）==========
    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication,
                                               AuthorizationContext context) {
        return authentication
                .defaultIfEmpty(new AnonymousAuthenticationToken()) // 未登录走 denied
                .map(auth -> {
                    ServerHttpRequest request = context.getExchange().getRequest();
                    String path = request.getURI().getPath();
                    if (auth instanceof AnonymousAuthenticationToken) {
                        AuthorizationDeniedException deniedException = new AuthorizationDeniedException("not authenticated");
                        boolean match = isWhite(path);
                        if(match) {
                            return new AuthorizationDecision(true);
                        }
                        return deniedException;
                    }

                    log.info("can access");
                    // TODO：接你之前的 Nacos/Redis path-role 规则
                    // String matchedRole = findMatchedRole(context.getExchange());
                    // boolean hasRole = auth.getAuthorities().stream()
                    //     .anyMatch(a -> a.getAuthority().equals("ROLE_" + matchedRole));
                    // return hasRole
                    //     ? AuthorizationResult.granted("has role")
                    //     : AuthorizationResult.denied("no matching role");
                    // 暂态：JWT 验过就放行（纯登录态校验，不判角色）

                    return new AuthorizationDecision(true);
                });
    }

    // ===== 白名单判断（Consul KV → Environment → auth.white-list）=====
    private boolean isWhite(String path) {
        String whiteListStr = environment.getProperty("auth.white-list", "/auth/**");
        if (whiteListStr == null || whiteListStr.isBlank()) {
            return false;
        }
        List<String> patterns = Arrays.stream(whiteListStr.split(","))
                .map(String::trim)
                .toList();
        return patterns.stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    // 匿名 token（authorize 里 defaultIfEmpty 用）
    static class AnonymousAuthenticationToken implements Authentication {
        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of();
        }
        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public @Nullable Object getDetails() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return null;
        }

        @Override
        public String getName() {
            return "anonymous";
        }
        @Override
        public boolean isAuthenticated() {
            return false;
        }
        @Override
        public void setAuthenticated(boolean isAuthenticated) {

        }

        @Override
        public Builder<?> toBuilder() {
            return Authentication.super.toBuilder();
        }
    }

    //    @Override
//    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication,
//                                             AuthorizationContext context) {
//        String path = context.getExchange().getRequest().getURI().getPath();
//
//        // 1️、白名单（从 Environment 读，Consul Watch 刷 RefreshEvent 后 Env 已更新 → 最新值）
//        String whiteListStr = environment.getProperty("auth.white-list", "");
//        if (whiteListStr != null && !whiteListStr.isBlank()) {
//            List<String> whitePatterns = Arrays.stream(whiteListStr.split(","))
//                    .map(String::trim)
//                    .toList();
//            boolean isWhite = whitePatterns.stream()
//                    .anyMatch(p -> pathMatcher.match(p, path));
//            if (isWhite) {
//                return Mono.just(new AuthorizationDecision(true));   // 放行，不走 JWT
//            }
//        }
//
//        // 2️、白名单 → 走 JWT 校验（manager 返回 empty = "继续 filter 链"，
//        //   下一级是 oauth2ResourceServer(jwt) → Nimbus 验签 → 成功再回 manager 二次判断角色）
//        //    如果你要做"JWT 验完后按角色判"，在这里接：
//        return authentication
//                .map(Authentication::getAuthorities)
//                .defaultIfEmpty(Collections.emptyList())
//                .map(authorities -> {
//                    // TODO: 这里可以按 authorities 判角色（JWT 里 `scope` / `roles` claim）
//                    //       或 merge 你 Nacos/Redis 的 path-role 规则
//                    // 暂时：JWT 验过就放行（纯登录态校验，不判角色）
//                    return new AuthorizationDecision(!authorities.isEmpty());
//                });
//    }
}
