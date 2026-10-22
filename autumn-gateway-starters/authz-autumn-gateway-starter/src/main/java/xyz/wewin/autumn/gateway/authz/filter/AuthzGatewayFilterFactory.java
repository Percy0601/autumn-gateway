package xyz.wewin.autumn.gateway.authz.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.wewin.autumn.gateway.authz.autoconfigure.AuthzGatewayProperties;
import xyz.wewin.autumn.gateway.authz.jwt.AuthzClaims;
import xyz.wewin.autumn.gateway.authz.jwt.AuthzJwtService;
import xyz.wewin.autumn.gateway.authz.rbac.AuthzRbacRepository;
import xyz.wewin.autumn.gateway.authz.rbac.AuthzRole;
import xyz.wewin.autumn.gateway.authz.support.AuthzJson;
import xyz.wewin.autumn.gateway.authz.support.AuthzResourceMatcher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * 路由级认证/鉴权过滤器工厂，路由上写 {@code Authz=authenticate,<appCode>} 或 {@code Authz=authorize,<appCode>}。
 *
 * <h3>三种状态，全靠「挂不挂这个过滤器」决定</h3>
 * <ol>
 *   <li><b>匿名</b>：路由没挂 {@code Authz}，本模块完全不介入——没有 GlobalFilter，
 *       也没有 SecurityWebFilterChain，不会误伤健康检查、静态资源；</li>
 *   <li><b>authenticate</b>：只校验 {@code Authorization: Bearer <jwt>}，过了就转发，不查库；</li>
 *   <li><b>authorize</b>：先验 JWT，再按 route 的 appCode 查角色 → 查资源 →
 *       用 AntPathMatcher + HTTP method 判定；管理员（{@code admin}）或
 *       管理员角色（{@code role_master = 1}）直通。</li>
 * </ol>
 *
 * <h3>凭据来源只有一处</h3>
 * 只读标准 {@code Authorization: Bearer}，<b>不接受 query token</b>——
 * URL 里的令牌会被浏览器历史、Referer、网关访问日志完整记录下来。
 *
 * <h3>通过后写入可信头</h3>
 * 先删掉入站的同名头（否则客户端可以自己伪造 {@code X-Autumn-User-Id}），再覆盖写入；
 * {@code Authorization: Bearer} 原样保留，方便下游服务自己再验一次。
 */
public class AuthzGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthzGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(AuthzGatewayFilterFactory.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    private final AuthzJwtService jwtService;
    private final AuthzRbacRepository rbacRepository;
    private final AuthzGatewayProperties properties;

    public AuthzGatewayFilterFactory(AuthzJwtService jwtService,
                                     AuthzRbacRepository rbacRepository,
                                     AuthzGatewayProperties properties) {
        super(Config.class);
        this.jwtService = jwtService;
        this.rbacRepository = rbacRepository;
        this.properties = properties;
        if (rbacRepository == null) {
            log.warn("未配置 DataSource：Authz=authorize 路由将按 fail-closed 拒绝（除非该路由显式打开 fail-open）");
        }
    }

    /** 短写顺序：{@code Authz=authorize,order} → mode + appCode */
    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("mode", "appCode");
    }

    @Override
    public GatewayFilter apply(Config config) {
        Config cfg = (config == null) ? new Config() : config;
        AuthzMode mode = AuthzMode.of(cfg.getMode());
        String appCode = StringUtils.hasText(cfg.getAppCode()) ? cfg.getAppCode() : this.properties.getAppCode();

        return (exchange, chain) -> {
            if (cfg.isPermitAll()) {
                return chain.filter(exchange);
            }
            String token = bearerToken(exchange);
            if (token == null) {
                return unauthorized(exchange, "缺少 Authorization: Bearer 令牌");
            }
            Optional<AuthzClaims> parsed = this.jwtService.parse(token);
            if (parsed.isEmpty()) {
                return unauthorized(exchange, "令牌无效或已过期");
            }
            AuthzClaims claims = parsed.get();
            long userId = parseUserId(claims.userId());
            if (userId <= 0L) {
                return unauthorized(exchange, "令牌缺少有效的 user_id");
            }

            ServerWebExchange trusted = withTrustedHeaders(exchange, claims);
            if (mode == AuthzMode.AUTHENTICATE) {
                return chain.filter(trusted);
            }
            return authorize(trusted, chain, cfg, appCode, claims, userId);
        };
    }

    private Mono<Void> authorize(ServerWebExchange exchange, GatewayFilterChain chain,
                                 Config config, String appCode, AuthzClaims claims, long userId) {
        if (this.rbacRepository == null) {
            return failClosedOrOpen(exchange, chain, config, null, "未配置 DataSource，无法完成鉴权");
        }
        String path = exchange.getRequest().getURI().getPath();
        String method = methodOf(exchange);

        return this.rbacRepository.findRoles(userId, appCode)
                .flatMap(roles -> {
                    boolean master = roles.stream().anyMatch(AuthzRole::master);
                    if (claims.admin() || master) {
                        return chain.filter(exchange);
                    }
                    if (roles.isEmpty()) {
                        return forbidden(exchange, "用户在应用 " + appCode + " 下没有有效角色");
                    }
                    return Flux.fromIterable(roles)
                            .concatMap(role -> this.rbacRepository.findResources(appCode, role.id()))
                            .flatMapIterable(resources -> resources)
                            .any(resource -> AuthzResourceMatcher.matches(resource, path, method))
                            .flatMap(matched -> Boolean.TRUE.equals(matched)
                                    ? chain.filter(exchange)
                                    : forbidden(exchange, "未授权访问 " + method + " " + path));
                })
                .onErrorResume(error -> failClosedOrOpen(exchange, chain, config, error, "鉴权查询失败"));
    }

    private Mono<Void> failClosedOrOpen(ServerWebExchange exchange, GatewayFilterChain chain,
                                        Config config, Throwable error, String message) {
        if (config.isFailOpen()) {
            log.warn("{}，按 fail-open 放行（该路由已显式开启）", message, error);
            return chain.filter(exchange);
        }
        log.warn("{}，按 fail-closed 拒绝", message, error);
        return forbidden(exchange, message);
    }

    /**
     * 覆写可信头：先删后写，杜绝客户端自带同名头伪造身份；
     * {@code Authorization} 保留，下游仍可自行验签。
     */
    private ServerWebExchange withTrustedHeaders(ServerWebExchange exchange, AuthzClaims claims) {
        AuthzGatewayProperties.Headers names = this.properties.getHeaders();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(names.getUserId());
                    headers.remove(names.getUsername());
                    headers.remove(names.getRoles());
                    headers.set(names.getUserId(), claims.userId());
                    headers.set(names.getUsername(), claims.username());
                    headers.set(names.getRoles(), claims.rolesHeader());
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    /** 只认标准 {@code Authorization: Bearer}；cookie / query token 一律不接受。 */
    private static String bearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String value = header.trim();
        if (value.length() <= BEARER_PREFIX.length()
                || !value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = value.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static String methodOf(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        return method == null ? "" : method.name();
    }

    private static long parseUserId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return -1L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.UNAUTHORIZED, "unauthorized", message);
    }

    private static Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.FORBIDDEN, "forbidden", message);
    }

    private static Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status,
                                        String error, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(WWW_AUTHENTICATE, BEARER_PREFIX.trim());
        }
        byte[] body = AuthzJson.object(
                        "status", status.value(),
                        "error", error,
                        "message", message,
                        "path", exchange.getRequest().getURI().getPath())
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 路由过滤器配置。{@code mode} 与 {@code appCode} 支持短写 {@code Authz=authorize,order}，
     * 其余项走完整 args 写法。
     *
     * <p><b>fail-open 默认 false</b>：鉴权查询出错（数据库抖动）时默认拒绝。
     * 故障放行是很危险的默认值——数据库一挂，全站接口就都变成匿名可访问了。
     * 只有在「可用性优先于安全性」的只读路由上才显式打开。</p>
     */
    public static class Config {

        /** {@code authenticate} 只验令牌；{@code authorize} 验令牌后再查 RBAC */
        private String mode = AuthzMode.AUTHENTICATE.code;

        /** 应用编码：角色与资源都按它查询；留空时用 {@code autumn.authz.app-code} */
        private String appCode = "";

        /** 鉴权查询出错时放行（默认拒绝） */
        private boolean failOpen = false;

        /** 完全跳过校验（用于灰度摘除鉴权但保留路由配置） */
        private boolean permitAll = false;

        public String getMode() {
            return this.mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getAppCode() {
            return this.appCode;
        }

        public void setAppCode(String appCode) {
            this.appCode = appCode;
        }

        public boolean isFailOpen() {
            return this.failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }

        public boolean isPermitAll() {
            return this.permitAll;
        }

        public void setPermitAll(boolean permitAll) {
            this.permitAll = permitAll;
        }
    }
}
