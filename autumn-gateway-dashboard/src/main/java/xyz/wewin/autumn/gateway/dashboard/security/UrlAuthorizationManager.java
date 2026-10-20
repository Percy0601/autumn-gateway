package xyz.wewin.autumn.gateway.dashboard.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * URL 级动态授权管理器：把请求路径与用户被授权的资源做匹配。
 *
 * <p>这是「后端接口鉴权」和「数据库资源配置」之间的桥：
 * 规则全部存在 {@code basic_resource} + {@code basic_role_permission} 里，
 * 不写死在代码中，因此运维调整权限不需要发版。</p>
 *
 * <h3>判定顺序</h3>
 * <ol>
 *   <li>未认证 → 拒绝（交给 AuthenticationEntryPoint 返回 401）</li>
 *   <li>超管 → 放行</li>
 *   <li>请求路径落在「匿名白名单」 → 放行（白名单在 SecurityConfig 已 permitAll，这里是双保险）</li>
 *   <li>用户的 URL 资源里有任意一条匹配 方法 + 路径 → 放行</li>
 *   <li>否则 → 拒绝（403）</li>
 * </ol>
 *
 * <h3>注意：默认拒绝</h3>
 * 第 5 步是拒绝而不是放行。新加一个接口如果忘了配 {@code basic_resource}，
 * 结果是「403 报错」而不是「悄悄对外开放」——前者会被人发现并修好，
 * 后者可能几个月都没人注意到。
 *
 * <h3>Spring Security 7 接口变更</h3>
 * 6.x 的 {@code AuthorizationManager#check} 在 7.0 中已被 {@code authorize} 取代
 * （返回 {@link AuthorizationResult}，{@link AuthorizationDecision} 是它的实现）。
 * 网上大量 6.x 教程写的是 {@code check}，直接抄会编译失败。
 */
@Component
public class UrlAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Logger log = LoggerFactory.getLogger(UrlAuthorizationManager.class);

    private static final AuthorizationDecision GRANT = new AuthorizationDecision(true);
    private static final AuthorizationDecision DENY = new AuthorizationDecision(false);

    /**
     * 始终允许匿名访问的路径前缀。
     *
     * <p>与 SecurityConfig 里的 permitAll 保持一致——两处都要有：
     * 一处负责「过滤器链不拦」，一处负责「授权管理器不判」。
     * 改这里时务必同步改 SecurityConfig。</p>
     */
    private static final String[] ALWAYS_PERMIT = {
            "/api/login/",
            "/static/",
            "/assets/",
            "/favicon.ico",
            "/error",
    };

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        Authentication auth = authentication.get();

        // 1. 未认证：交回给 Spring Security 走 401
        if (auth == null || !auth.isAuthenticated()) {
            return DENY;
        }

        Object principal = auth.getPrincipal();
        if (!(principal instanceof LoginUser user)) {
            // 未登录时 principal 是字符串 "anonymousUser"，静默拒绝即可，
            // 由 AuthenticationEntryPoint 统一返回 401，不必污染日志。
            // 非匿名却拿不到 LoginUser 才是真异常（说明有别的认证方式接进来了），需要告警。
            if (!(auth instanceof AnonymousAuthenticationToken)) {
                log.warn("认证主体不是 LoginUser（实际 {}），按拒绝处理。"
                                + "请检查是否有其它 AuthenticationProvider 接入了过滤器链",
                        principal == null ? "null" : principal.getClass().getName());
            }
            return DENY;
        }

        // 2. 超管直通
        if (user.isSuperAdmin()) {
            return GRANT;
        }

        HttpServletRequest request = context.getRequest();
        String path = request.getRequestURI();
        // 去掉 context-path，保证 /dashboard/api/xxx 与 /api/xxx 判定一致
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String method = request.getMethod();

        // 3. 白名单双保险
        for (String prefix : ALWAYS_PERMIT) {
            if (path.startsWith(prefix)) {
                return GRANT;
            }
        }

        // 4. 资源匹配
        UserGrant grant = user.grant();
        for (UrlResource resource : grant.urlResources()) {
            if (resource.matches(path, method)) {
                log.debug("授权通过 user={} {} {} <- 资源#{} {}",
                        user.getUserId(), method, path, resource.id(), resource.name());
                return GRANT;
            }
        }

        // 5. 默认拒绝
        log.warn("拒绝访问：user={}（{}）{} {} 未匹配任何已授权资源，权限数={}",
                user.getUserId(), user.getUsername(), method, path, grant.size());
        return DENY;
    }
}
