package xyz.wewin.autumn.gateway.dashboard.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全局唯一的 JWT 过滤器：解析并验签 token，然后写入两处上下文：
 * <ul>
 *   <li>{@link RequestContext} —— 业务审计字段（create_user / update_user / op_user_id）
 *       取「当前操作人」的唯一来源，与 Spring Security 是否开启无关；</li>
 *   <li>{@link SecurityContextHolder} —— Spring Security 认证态，
 *       后续在网关或本服务开启鉴权后可直接复用。</li>
 * </ul>
 *
 * <p>token 只解析一次（验签 + 过期校验）。token 缺失或非法时按匿名处理、不抛异常，
 * 保证既有接口行为不变；请求结束时清理 ThreadLocal，避免线程复用串号。
 *
 * <p>注意：不要再把本类命名为 RequestContextFilter —— 该 bean 名已被 Spring Boot 的
 * {@code WebMvcAutoConfiguration} 占用，会导致 BeanDefinitionOverrideException。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
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
                    Claims claims = jwtUtil.parseToken(token);
                    Long userId = Long.parseLong(claims.getSubject());
                    String username = claims.get("username", String.class);

                    RequestContext.set(RequestContext.of(userId, username, token));
                    // 认证对象：principal=userId，无密码，无角色
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userId, null, null));
                } catch (JwtException | IllegalArgumentException e) {
                    // token 非法/过期：按匿名处理，接口行为保持与之前一致
                    RequestContext.set(RequestContext.anonymous());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 优先取 Authorization 头；网关若以其它方式透传，可回退到 token 参数。
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
