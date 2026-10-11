package xyz.wewin.autumn.gateway.dashboard.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * 网关兼容配置：让 dashboard 服务「有网关可用、没网关也能独立跑」。
 *
 * <p>核心是 {@link ForwardedHeaderFilter}，它会解析网关附加的转发头：
 * <ul>
 *   <li>{@code X-Forwarded-Host} / {@code X-Forwarded-Port}：还原真实访问地址</li>
 *   <li>{@code X-Forwarded-Proto}：还原 http/https</li>
 *   <li>{@code X-Forwarded-Prefix}：还原网关挂载的子路径（如 {@code /dashboard}），
 *       使服务端生成的链接带上正确前缀</li>
 * </ul>
 * 独立部署时请求中不携带这些头，过滤器不产生任何副作用，行为与原来完全一致。
 *
 * <p>网关侧的路由建议（二选一）：
 * <ol>
 *   <li>网关 {@code StripPrefix=1}：转发到本服务时剥掉 {@code /dashboard}，
 *       服务看到的仍是 {@code /api/**}，无需额外配置；</li>
 *   <li>网关不剥离前缀：则为本服务配置 {@code server.servlet.context-path=/dashboard}。</li>
 * </ol>
 * 前端静态资源使用相对路径（vite {@code base: './'}），两种方式都能正确加载。
 */
@Configuration
public class GatewayCompatConfig {

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        // 必须早于其它过滤器执行，才能让后续组件拿到还原后的请求信息
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
