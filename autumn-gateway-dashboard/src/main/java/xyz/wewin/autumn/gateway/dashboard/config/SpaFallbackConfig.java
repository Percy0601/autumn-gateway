package xyz.wewin.autumn.gateway.dashboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 前端 SPA（history 模式）路由回退配置。
 *
 * <h3>解决什么问题</h3>
 * <p>dashboard-ui 使用 {@code createWebHistory()}（history 模式），前端路由形如
 * {@code /dashboard/index}、{@code /system/user/index}。这些地址在前端由 vue-router
 * 拦截渲染；但用户在深链上按 F5、或浏览器直接访问时，会真的向后端发起一个 GET。
 * 后端既没有对应资源也没有对应 Controller，Spring Boot 于是返回 Whitelabel 404
 * —— 对用户而言就是又一个「错误页 / 白屏」。本项目原本「退出登录 →
 * {@code location.reload()}」正好踩中这个坑。</p>
 *
 * <h3>为什么用「显式前缀 + ResourceResolver」而不是 catch-all Controller</h3>
 * <ol>
 *   <li><b>不能压过 {@code /api/**}</b>：若用 {@code @GetMapping("/{*path}")} 之类的
 *       通配 Controller 兜底，它会作为 {@code RequestMappingHandlerMapping} 命中，
 *       优先级高于静态资源处理器，把 {@code /api/**} 的 404、甚至接口响应一起吞掉，
 *       前端 {@code JSON.parse} 直接报错。本方案只注册 7 个前端前缀，
 *       对 {@code /api/**} 零覆盖。</li>
 *   <li><b>不能压过静态资源</b>：通配 Controller 会先于静态资源处理器命中
 *       {@code /assets/index-xxx.js}，把所有 JS/CSS 打成 404 —— 那就是整站白屏，
 *       正是本次事故要避免的形态。本方案的 resolver 只在「目标文件确实不存在」时
 *       才回落到 index.html，`/assets/**` 完全不经过这里。</li>
 *   <li><b>不顶掉 Spring Boot 默认错误处理</b>：若自定义 {@code ErrorController}，
 *       由于 {@code BasicErrorController} 带
 *       {@code @ConditionalOnMissingBean(ErrorController.class)}，会把全局错误
 *       响应整体替换掉，必须完整复刻 JSON 错误体，风险面太大。</li>
 * </ol>
 *
 * <h3>边界</h3>
 * <ul>
 *   <li>只对 {@code ResourceHttpRequestHandler} 支持的 <b>GET / HEAD</b> 生效；
 *       POST/PUT/DELETE 不会落到这里。</li>
 *   <li>带文件后缀的静态资源（.js/.css/.png/.ico/.svg/.woff2 ...）不在模式内，
 *       仍走 Spring Boot 默认静态资源处理，找不到就正常 404。</li>
 * </ul>
 *
 * <h3>⚠️ 维护约定</h3>
 * <p><b>新增前端一级业务路由时，必须同步把该前缀加到下面的 {@code addResourceHandler}
 * 列表里</b>，否则该路由的 F5 刷新仍会 404。当前列表与 dashboard-ui 的
 * {@code src/router} + {@code src/views} 目录结构一一对应。</p>
 *
 * <h3>⚠️ 已知连带的限制（开启 RBAC 时才会暴露）</h3>
 * <p>当 {@code basic.rbac.enabled=true} 时，{@code SecurityConfig} 用的是
 * {@code anyRequest().authenticated()}，而它的 permitAll 白名单目前只含
 * {@code /}、{@code /index.html}、{@code /assets/**}、{@code /static/**}、
 * {@code /favicon.*}、{@code /error}。浏览器直接 GET 深链（页面导航不带
 * {@code Authorization} 头）会在进入 Spring MVC 之前就被 Spring Security
 * 拦成 401 JSON，本回退拿不到机会。开启 RBAC 前需要把下面这些前端路由前缀
 * 一并加入 {@code SecurityConfig} 的 permitAll 白名单。</p>
 * <p>默认配置（{@code basic.rbac.enabled=false}）下走 {@code anyRequest().permitAll()}，
 * 本回退可正常工作。</p>
 */
@Configuration
public class SpaFallbackConfig implements WebMvcConfigurer {

    /**
     * 前端 vue-router 的一级路由前缀。
     *
     * <ul>
     *   <li>{@code /login} —— 登录页（constantRoutes）</li>
     *   <li>{@code /404} —— 404 页（constantRoutes）</li>
     *   <li>{@code /dashboard/**} —— 首页看板</li>
     *   <li>{@code /system/**} —— 系统管理各页（前端页面路径；
     *       注意后端接口在 {@code /api/system/...}，两者不冲突）</li>
     *   <li>{@code /p/**} —— 目录型菜单的占位路由（generateRoutes 生成）</li>
     *   <li>{@code /redirect/**} —— 重定向中转页</li>
     *   <li>{@code /iframe/**} —— 内嵌外链页</li>
     * </ul>
     */
    private static final String[] SPA_ROUTE_PATTERNS = {
            "/login",
            "/404",
            "/dashboard/**",
            "/system/**",
            "/p/**",
            "/redirect/**",
            "/iframe/**"
    };

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(SPA_ROUTE_PATTERNS)
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 1) 真实存在的静态文件优先（例如将来放个 /system/logo.png）
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 2) 文件不存在 → 一律回落 index.html，交给前端 vue-router 处理
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
