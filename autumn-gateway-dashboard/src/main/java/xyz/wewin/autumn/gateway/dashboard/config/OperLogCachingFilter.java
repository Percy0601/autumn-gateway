package xyz.wewin.autumn.gateway.dashboard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * 为请求包一层 {@link ContentCachingRequestWrapper}，使请求体可被重复读取，
 * 供 {@link OperLogInterceptor} 在 afterCompletion 中安全地读取 POST/PUT/DELETE 的请求数据。
 *
 * <p>必须在 DispatcherServlet 读取 body 之前包裹，故置于最高优先级；只对可能带 JSON body 的
 * 写操作包裹，其余透传，避免无谓拷贝（尤其文件上传 / 大 body 场景）。
 */
@Component
public class OperLogCachingFilter extends OncePerRequestFilter implements Ordered {

    /** 请求体缓存上限，对齐旧版单参构造器的默认 1MB（足以覆盖审计所需的 JSON） */
    private static final int MAX_CACHED_BODY = 1024 * 1024;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isJsonBody(request)) {
            ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request, MAX_CACHED_BODY);
            filterChain.doFilter(wrapped, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private boolean isJsonBody(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method)
                && !"DELETE".equals(method) && !"PATCH".equals(method)) {
            return false;
        }
        String ct = request.getContentType();
        return ct != null && ct.toLowerCase().contains("application/json");
    }
}
