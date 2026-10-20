package xyz.wewin.autumn.gateway.dashboard.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicOperLog;
import xyz.wewin.autumn.gateway.dashboard.security.RequestContext;
import xyz.wewin.autumn.gateway.dashboard.service.OperLogRecorder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 统一操作日志拦截器（Spring MVC HandlerInterceptor）。
 *
 * <p>之所以用 HandlerInterceptor 而非原始 Servlet Filter：本拦截器在 DispatcherServlet 内部、
 * JWT 过滤器清理 {@link RequestContext} 之前执行，可可靠取得「当前操作人」，以及响应状态码与异常；
 * 同时只在真正进入 Controller 的写操作上记录，避免污染静态资源 / GET / 404。
 *
 * <p>只针对 POST / PUT / DELETE 写操作落库；登录、鉴权类接口（含敏感凭证）主动跳过。
 */
@Component
public class OperLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OperLogInterceptor.class);

    private static final int MAX_TEXT = 2000;
    private static final String START_ATTR = "operLogStartTime";
    // 这些接口的请求体含明文凭证（密码 / token），落审计日志等于把凭据写进库里，必须跳过。
    // reset-password 的响应里还带明文新密码，同样不能记录请求体。
    private static final String AUTH_SKIP = "/login,/auth,/oauth,/token,/reset-password";

    private static final Map<String, String> MODULE_CN = Map.of(
            "user", "用户", "role", "角色", "group", "用户组",
            "namespace", "命名空间", "app", "应用", "menu", "菜单",
            "resource", "资源", "oper-log", "操作日志");

    private final OperLogRecorder recorder;
    private final String appCode;

    public OperLogInterceptor(OperLogRecorder recorder,
                              @Value("${spring.application.name:autumn-gateway}") String appCode) {
        this.recorder = recorder;
        this.appCode = appCode;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute(START_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // 仅记录真正进入 Controller 的写操作；静态资源 / GET / 404 / 无 handler 不记
        if (!(handler instanceof HandlerMethod)) {
            return;
        }
        String method = request.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            return;
        }
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api") || isAuthUri(uri)) {
            return;
        }

        try {
            recorder.record(build(request, response, (HandlerMethod) handler, ex));
        } catch (Exception e) {
            // 审计日志写失败绝不影响主流程
            log.warn("操作日志落库失败（不影响主流程）：{}", e.getMessage());
        }
    }

    private boolean isAuthUri(String uri) {
        String lower = uri.toLowerCase();
        for (String keyword : AUTH_SKIP.split(",")) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private BasicOperLog build(HttpServletRequest request, HttpServletResponse response,
                               HandlerMethod handler, Exception ex) {
        long start = (Long) request.getAttribute(START_ATTR);
        long consume = System.currentTimeMillis() - start;

        String module = resolveModule(request.getRequestURI());
        BasicOperLog entity = new BasicOperLog();
        entity.setAppCode(truncate(appCode, 50));
        entity.setBusinessType(truncate(module, 50));
        entity.setTitle(truncate(MODULE_CN.getOrDefault(module, module), 100));
        entity.setRequestId(truncate(UUID.randomUUID().toString(), 50));
        entity.setRequestUrl(truncate(request.getRequestURI(), 200));
        entity.setRequestMethod(truncate(request.getMethod(), 100));
        entity.setRequestData(truncate(readBody(request), MAX_TEXT));
        entity.setClassMethod(truncate(
                handler.getBeanType().getName() + "." + handler.getMethod().getName(), 200));
        entity.setConsumeTime((int) Math.min(consume, Integer.MAX_VALUE));
        entity.setResponseData("");
        entity.setStatus((ex != null || response.getStatus() >= 400) ? 1 : 0);
        entity.setErrorMsg(truncate(ex == null ? "" : ex.getMessage(), MAX_TEXT));
        entity.setOperId(RequestContext.currentUserId());
        entity.setOperIp(truncate(clientIp(request), 50));
        entity.setOpTime(LocalDateTime.now());
        return entity;
    }

    private String readBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] bytes = wrapper.getContentAsByteArray();
            if (bytes != null && bytes.length > 0) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /** /api/system/basic/user/123 -> user */
    private String resolveModule(String uri) {
        if (uri == null) {
            return "";
        }
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("basic".equals(parts[i])) {
                return parts[i + 1];
            }
        }
        return parts.length > 2 ? parts[2] : "";
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String v, int max) {
        if (v == null) {
            return "";
        }
        return v.length() > max ? v.substring(0, max) : v;
    }
}
