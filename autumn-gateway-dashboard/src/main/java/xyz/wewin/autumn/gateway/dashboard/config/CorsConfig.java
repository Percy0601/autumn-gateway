package xyz.wewin.autumn.gateway.dashboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置：允许前端页面直连本服务接口。
 *
 * <p>dashboard-ui 执行 npm run build 后，产物可能托管于本地静态服务器 / Nginx
 * （与 8080 不同源），浏览器访问 {@code http://localhost:8080} 上的
 * {@code /api/login/account}、{@code /api/currentUser} 等接口会产生跨域请求。
 * 本地调试场景放开所有来源，认证依赖 JWT（Authorization 头）而非 Cookie。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
