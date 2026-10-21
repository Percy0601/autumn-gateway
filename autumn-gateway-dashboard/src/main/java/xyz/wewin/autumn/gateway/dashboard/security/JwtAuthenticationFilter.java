package xyz.wewin.autumn.gateway.dashboard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRepository;

import java.io.IOException;
import java.util.Optional;

/**
 * 全局唯一的 JWT 过滤器：解析并验签 token，然后写入两处上下文：
 * <ul>
 *   <li>{@link RequestContext} —— 业务审计字段（create_user / update_user / op_user_id）
 *       取「当前操作人」的唯一来源；</li>
 *   <li>{@link SecurityContextHolder} —— Spring Security 认证态，供鉴权使用。</li>
 * </ul>
 *
 * <h3>v7 变更：令牌改由授权服务器签发</h3>
 * <ul>
 *   <li>本服务<b>不再自签令牌</b>，只做验签 —— 验签密钥来自授权服务器的 JWKS
 *       （{@code autumn.security.jwt.jwk-set-uri}），由 {@link JwtTokenService} 用 Nimbus 完成；</li>
 *   <li>令牌 {@code sub} 是<b>用户 uuid</b>，而 {@code basic_user.id} 是自增序列，
 *       两者不是同一个 id 空间，必须先用 uuid 反查到本服务的用户记录；</li>
 *   <li>权限仍由 {@link PermissionService} 从 basic_* 表装配（令牌里的 authorities 只是参考，不参与判权）；</li>
 *   <li><b>保留</b>原有「token 非法按匿名处理、不抛异常」的语义：
 *       是否拦截由 {@code authorizeHttpRequests} 决定，
 *       本过滤器只负责「尽力识别身份」，保持职责单一。</li>
 * </ul>
 *
 * <p>注意：不要再把本类命名为 RequestContextFilter —— 该 bean 名已被 Spring Boot 的
 * {@code WebMvcAutoConfiguration} 占用，会导致 BeanDefinitionOverrideException。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final PermissionService permissionService;
    private final BasicUserRepository basicUserRepository;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   PermissionService permissionService,
                                   BasicUserRepository basicUserRepository) {
        this.jwtTokenService = jwtTokenService;
        this.permissionService = permissionService;
        this.basicUserRepository = basicUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (token == null) {
                RequestContext.set(RequestContext.anonymous());
            } else {
                try {
                    // 一次解析完成验签与过期校验，失败抛 JwtException
                    JwtTokenService.Identity identity = this.jwtTokenService.resolve(token);
                    BasicUser user = resolveUser(identity).orElse(null);
                    if (user == null) {
                        // 令牌有效但本服务没有对应账号（两套账号表尚未收敛），按匿名处理而非 500
                        log.warn("令牌有效但 basic_user 中找不到对应用户：uuid={} username={}",
                                identity.uuid(), identity.username());
                        RequestContext.set(RequestContext.anonymous());
                    } else {
                        RequestContext.set(RequestContext.of(user.getId(), user.getUsername(), token));

                        // 装配权限：缓存命中时无 SQL 开销
                        UserGrant grant = this.permissionService.load(user.getId());
                        LoginUser principal = new LoginUser(user.getId(), user.getUsername(), token, grant);

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

                        // 写入认证态。
                        //
                        // 这里 setContext 能生效的前提是 SecurityConfig 里注册了
                        // RequestAttributeSecurityContextRepository —— 它保证同一请求内
                        // 反复 getContext() 拿到的是同一个实例。
                        // 若用默认的 NullSecurityContextRepository，这里写进去的
                        // 认证态会在下游读取时被新生成的空 context 覆盖掉。
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(authentication);
                        SecurityContextHolder.setContext(context);

                        // 显式落盘：deferred 模式（SecurityContextHolderFilter）不会自动调用
                        // SecurityContextRepository#saveContext，必须由认证方自己保存，
                        // 否则 request attribute 里始终是那个空的初始 context。
                        request.setAttribute(
                                RequestAttributeSecurityContextRepository.ATTR_NAME, context);
                    }
                } catch (JwtException | IllegalArgumentException e) {
                    // token 非法/过期：按匿名处理，由后续授权规则决定是否放行
                    log.debug("JWT 解析失败，按匿名处理: {}", e.getMessage());
                    RequestContext.set(RequestContext.anonymous());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            // 不要在这里 clearContext()！
            //
            // filterChain.doFilter(...) 内部会依次执行后续的 Security 过滤器
            // （AnonymousAuthenticationFilter → AuthorizationFilter），
            // 它们在 filterChain 返回**之前**就要读到认证态。
            // 如果在 finally 里清空，授权阶段拿到的会是 Spring Security
            // 补的匿名主体（principal = "anonymousUser" 字符串），
            // 表现为「登录成功但每个接口都 401」——这个坑踩过一次。
            //
            // SecurityContextHolderFilter 会在请求结束时自动清理，无需手写。
        }
    }

    /**
     * 令牌身份 → 本服务的 basic_user 记录。
     *
     * <p>优先用 uuid（令牌 sub），缺失时回落到 username。
     * 两条腿是因为两套账号表（user 与 basic_user）尚未合并，
     * uuid 在两边已对齐，username 作为兜底。</p>
     */
    private Optional<BasicUser> resolveUser(JwtTokenService.Identity identity) {
        if (identity.uuid() != null && !identity.uuid().isBlank()) {
            Optional<BasicUser> byUuid = this.basicUserRepository.findByUuid(identity.uuid());
            if (byUuid.isPresent()) {
                return byUuid;
            }
        }
        if (identity.username() != null && !identity.username().isBlank()) {
            return this.basicUserRepository.findByUsername(identity.username());
        }
        return Optional.empty();
    }

    /**
     * 优先取 Authorization 头；网关若以其它方式透传，可回退到 token 参数。
     *
     * <p>安全提示：token 放在 query 参数里会被 Nginx access_log、浏览器历史和 Referer
     * 记录，属于凭据泄露的常见来源。保留此回退仅为兼容既有网关透传方式，
     * 若可以收敛，建议后续只接受 Authorization 头。</p>
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        String param = request.getParameter("token");
        return (param == null || param.isBlank()) ? null : param.trim();
    }
}
