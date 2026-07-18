package xyz.wewin.autumn.gateway.examples.oidc.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import reactor.core.publisher.Mono;

/**
 * 动态响应式授权管理器
 * 支持白名单放行 + JWT 验签后二次判角色
 *
 * @author: baoxin.zhao
 * @date: 7/4/26
 */
@Component
public class DynamicReactiveAuthorizationManager
        implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Environment environment;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    public DynamicReactiveAuthorizationManager(Environment environment) {
        this.environment = environment;
    }

    /**
     * 授权判断：JWT 验签后二次判角色（path-role 动态规则）
     */
    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication,
                                               AuthorizationContext context) {
        return authentication
                .defaultIfEmpty(new AnonymousAuthenticationToken())
                .map(auth -> {
                    ServerHttpRequest request = context.getExchange().getRequest();
                    String path = request.getURI().getPath();

                    // 白名单放行
                    if (isWhite(path)) {
                        return new AuthorizationDecision(true);
                    }

                    // 未认证
                    if (auth instanceof AnonymousAuthenticationToken) {
                        return new AuthorizationDecision(false);
                    }

                    log.info("authenticated, path={}", path);
                    // TODO：接 Nacos/Redis path-role 规则
                    // String matchedRole = findMatchedRole(context.getExchange());
                    // boolean hasRole = auth.getAuthorities().stream()
                    //     .anyMatch(a -> a.getAuthority().equals("ROLE_" + matchedRole));
                    // return hasRole ? new AuthorizationDecision(true) : new AuthorizationDecision(false);

                    // 暂态：JWT 验过就放行（纯登录态校验，不判角色）
                    return new AuthorizationDecision(true);
                });
    }

    /**
     * 白名单判断（从 Environment 读配置）
     */
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

    /**
     * 匿名 token（authorize 里 defaultIfEmpty 用）
     */
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
            // no-op
        }

        @Override
        public Builder<?> toBuilder() {
            return Authentication.super.toBuilder();
        }
    }
}
