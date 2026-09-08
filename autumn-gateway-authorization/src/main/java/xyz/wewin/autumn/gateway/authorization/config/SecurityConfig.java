package xyz.wewin.autumn.gateway.authorization.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import xyz.wewin.autumn.gateway.authorization.security.CaptchaFilter;
import xyz.wewin.autumn.gateway.authorization.security.CaptchaService;
import xyz.wewin.autumn.gateway.authorization.security.LoginAttemptService;
import xyz.wewin.autumn.gateway.authorization.web.JsonResponses;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 普通安全配置：账号登录、页面权限
 *
 * <p>用户数据源为数据库（见 {@link xyz.wewin.autumn.gateway.authorization.security.JdbcUserDetailsService}），
 * 与 dashboard 共用 user + user_auth_account(password) 这套账号体系。</p>
 *
 * <p>登录页为自定义的 SPA 页面（默认登录页太简陋且无法承载验证码交互）；
 * 登录接口返回 JSON，便于前端根据错误码提示"验证码错误 / 账号锁定 / 还剩几次机会"。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;

    @Value("${autumn.security.captcha.enabled:true}")
    private boolean captchaEnabled;

    public SecurityConfig(CaptchaService captchaService, LoginAttemptService loginAttemptService) {
        this.captchaService = captchaService;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * 普通请求安全过滤器链：只放行登录页、静态资源与验证码/CSRF 接口
     */
    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/login", "/index.html", "/favicon.ico",
                                "/assets/**", "/api/captcha", "/api/csrf")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin((form) -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler())
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                // 验证码校验必须早于用户名密码认证
                .addFilterBefore(new CaptchaFilter(this.captchaService, this.captchaEnabled),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码编码器：容器中存在该 Bean 时授权服务器会直接用于校验客户端密钥，
     * 因此 oauth2_registered_client.client_secret 存的是无算法前缀的 BCrypt 串
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 登录成功：清零失败计数，并告诉前端下一步跳哪里
     * （若来自 /oauth2/authorize，会跳回原授权请求，保证授权码流程不断）
     */
    private AuthenticationSuccessHandler loginSuccessHandler() {
        return (request, response, authentication) -> {
            this.loginAttemptService.onSuccess(authentication.getName(), clientIp(request));
            SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
            String redirect = (savedRequest != null) ? savedRequest.getRedirectUrl() : "/";
            writeJson(response, HttpServletResponse.SC_OK,
                    Map.of("success", true, "redirect", redirect));
        };
    }

    /**
     * 登录失败：区分验证码/锁定/凭据错误，并回传剩余尝试次数
     */
    private AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String username = request.getParameter("username");
            String ip = clientIp(request);

            String code;
            String message;
            boolean locked;

            if (exception instanceof LockedException) {
                code = "ACCOUNT_LOCKED";
                locked = true;
                message = buildLockedMessage(username);
            } else if (exception instanceof DisabledException) {
                code = "ACCOUNT_DISABLED";
                locked = false;
                message = "账号已禁用，请联系管理员";
            } else if (exception instanceof BadCredentialsException) {
                // 用户名不存在也会被包装成 BadCredentialsException（避免账号枚举）
                code = "BAD_CREDENTIALS";
                locked = false;
                this.loginAttemptService.onFailure(username, ip);
                message = "用户名或密码错误";
            } else {
                code = "AUTH_FAILED";
                locked = false;
                this.loginAttemptService.onFailure(username, ip);
                message = "登录失败，请稍后重试";
            }

            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("code", code);
            body.put("message", message);
            body.put("locked", locked);
            body.put("remainingAttempts", locked ? 0 : this.loginAttemptService.remainingAttempts(username));
            body.put("maxAttempts", this.loginAttemptService.getMaxAttempts());

            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, body);
        };
    }

    private String buildLockedMessage(String username) {
        int seconds = this.loginAttemptService.lockedRemainingSeconds(username);
        if (seconds > 0) {
            long minutes = (seconds + 59) / 60;
            return "连续输入错误次数过多，账号已锁定，请在 " + minutes + " 分钟后重试";
        }
        return "连续输入错误次数过多，账号已锁定，请稍后重试";
    }

    private static void writeJson(HttpServletResponse response, int status, Map<String, Object> body)
            throws IOException {
        JsonResponses.write(response, status, body);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
