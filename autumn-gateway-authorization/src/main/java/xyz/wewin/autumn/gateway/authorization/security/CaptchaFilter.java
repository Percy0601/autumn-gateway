package xyz.wewin.autumn.gateway.authorization.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.wewin.autumn.gateway.authorization.web.JsonResponses;

import java.io.IOException;
import java.util.Map;

/**
 * 验证码校验过滤器：在用户名密码认证之前拦截 POST /login。
 *
 * <p>验证码不通过就直接返回，不会走到账号密码校验，从源头挡住暴力提交。</p>
 */
public class CaptchaFilter extends OncePerRequestFilter {

    private final CaptchaService captchaService;
    private final boolean enabled;

    public CaptchaFilter(CaptchaService captchaService,
                         @Value("${autumn.security.captcha.enabled:true}") boolean enabled) {
        this.captchaService = captchaService;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!this.enabled || !isLoginSubmit(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!this.captchaService.validate(request, request.getParameter("captcha"))) {
            JsonResponses.write(response, HttpStatus.UNAUTHORIZED.value(),
                    Map.of("success", false, "code", "CAPTCHA_INVALID", "message", "验证码错误或已失效"));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isLoginSubmit(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getServletPath());
    }
}
