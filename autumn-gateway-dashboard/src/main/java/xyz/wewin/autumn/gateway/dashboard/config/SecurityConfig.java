package xyz.wewin.autumn.gateway.dashboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import xyz.wewin.autumn.gateway.dashboard.security.JwtAuthenticationFilter;
import xyz.wewin.autumn.gateway.dashboard.security.RequestAttributeSecurityContextRepository;
import xyz.wewin.autumn.gateway.dashboard.security.UrlAuthorizationManager;

/**
 * Spring Security 配置（Spring Security 7 / Boot 4，仅支持 Lambda DSL）。
 *
 * <h3>三层鉴权</h3>
 * <ol>
 *   <li><b>认证</b>：{@link JwtAuthenticationFilter} 解析 Bearer token → {@code LoginUser}；</li>
 *   <li><b>接口准入（粗粒度）</b>：{@code authorizeHttpRequests} 决定「匿名 / 登录即可 / 走资源鉴权」；</li>
 *   <li><b>操作授权（细粒度）</b>：{@code @PreAuthorize("hasAuthority('system:user:assign-role')")}
 *       在 Controller 方法上做权限码管控，权限标识来自 {@code basic_menu.menu_identification}。</li>
 * </ol>
 *
 * <h3>管控策略（核心约定）</h3>
 * <pre>
 *   ┌── 匿名放行 ────────── 登录、验证码、静态资源、健康检查
 *   ├── 认证即可 ────────── 全部查询 + 新增/编辑（需求：新增类操作不重要，登录就能加）
 *   └── 权限管控 ────────── 授权类（assign-*）、删除、启停、重置密码
 *                            → 由 @PreAuthorize + 权限码决定
 * </pre>
 * 详见 {@code docs/权限与授权设计.md}。
 *
 * <h3>两个开关</h3>
 * <ul>
 *   <li>{@code basic.rbac.enabled}（默认 {@code true}）——总开关。
 *       {@code false} 时所有接口 permitAll，用于线上紧急回滚，改配置即可，不用改代码。</li>
 *   <li>{@code basic.rbac.url-check}（默认 {@code false}）——是否叠加「数据驱动的 URL 资源鉴权」
 *       （{@link UrlAuthorizationManager}，规则存 {@code basic_resource}，默认拒绝）。
 *       默认关闭：因为需求要求「新增类操作认证即可」，而 URL 层是默认拒绝的，
 *       两者冲突。等资源表维护完善、需要更严格的接口级收口时再打开。</li>
 * </ul>
 * 切换方式（无需改代码）：
 * <pre>
 *   java -jar app.jar --basic.rbac.enabled=true --basic.rbac.url-check=true
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UrlAuthorizationManager urlAuthorizationManager;
    private final boolean rbacEnabled;
    private final boolean urlCheckEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          UrlAuthorizationManager urlAuthorizationManager,
                          @Value("${basic.rbac.enabled:true}") boolean rbacEnabled,
                          @Value("${basic.rbac.url-check:false}") boolean urlCheckEnabled) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.urlAuthorizationManager = urlAuthorizationManager;
        this.rbacEnabled = rbacEnabled;
        this.urlCheckEnabled = urlCheckEnabled;
        log.info("RBAC 鉴权开关 basic.rbac.enabled = {}，URL 资源鉴权 basic.rbac.url-check = {}",
                rbacEnabled, urlCheckEnabled);
        if (!rbacEnabled) {
            log.warn("鉴权未启用：所有接口目前可匿名访问。上线前请置 basic.rbac.enabled=true");
        }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + JWT：不使用 Cookie 会话，CSRF 无攻击面，关闭是正确选择
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                // 完全无状态：不创建 Session，水平扩容无需粘性会话
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // JWT 过滤器插在用户名密码认证过滤器之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 关键：把 SecurityContext 换成「请求级共享实例」的仓库。
                // 不设这一行时用的是 NullSecurityContextRepository，它每次 load 都新建
                // 空 context，JWT 过滤器写入的认证态在下游读取时被丢弃
                // —— 表现为「登录成功但所有接口 401」。
                //
                // 注意必须放在 authorizeHttpRequests 之前：
                // SecurityContextConfigurer 是把仓库塞进 sharedObject 的，
                // 而 authorizeHttpRequests 的 configurer 在 init 阶段就会
                // 读取该 sharedObject 来构建 AuthorizationFilter。
                // 顺序反了就会退回默认仓库，症状与不配置完全一样。
                .securityContext(sc -> sc.securityContextRepository(
                        new RequestAttributeSecurityContextRepository()))
                .authorizeHttpRequests(auth -> {
                    // 顺序有讲究：从上到下，第一个匹配的规则生效。
                    // 具体规则必须排在 /api/** 之前，否则会被宽规则吞掉。

                    // 1) 静态资源与前端路由：放行（前端产物编译进 static）
                    //    /login、/404、/dashboard/**、/system/**、/p/**、/redirect/**、/iframe/**
                    //    是 vue-router 的 history 模式路由：浏览器深链/F5 刷新会真的发 GET，
                    //    不带 Authorization 头，必须匿名放行，由 SpaFallbackConfig
                    //    回落到 index.html 交给前端路由渲染。
                    auth.requestMatchers(
                            "/", "/index.html",
                            "/assets/**", "/static/**",
                            "/favicon.ico", "/favicon.svg",
                            "/login", "/404",
                            "/dashboard/**", "/system/**",
                            "/p/**", "/redirect/**", "/iframe/**",
                            "/error"
                    ).permitAll();

                    // 2) 登录相关：登录接口必须匿名可访问，否则谁也登不进来
                    //    /api/captcha 是登录页验证码，同样必须匿名可访问
                    auth.requestMatchers(
                            "/api/login/account",
                            "/api/login/outLogin",
                            "/api/captcha",
                            "/auth/**"
                    ).permitAll();

                    // 3) 探活：给 K8s / 负载均衡用，不需要凭据
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();

                    if (rbacEnabled) {
                        if (urlCheckEnabled) {
                            // 4a) 严格模式：/api/** 走数据驱动的 URL 资源鉴权
                            //     （规则存 basic_resource，未登记的接口一律 403）
                            auth.requestMatchers("/api/**")
                                    .access(urlAuthorizationManager);
                        } else {
                            // 4b) 标准模式（默认）：/api/** 只要登录即可。
                            //     细粒度管控全部下沉到方法级 @PreAuthorize ——
                            //     这样「新增/编辑认证即可」与「授权类操作必须授权」
                            //     两条需求可以同时成立，而不用把每一个只读接口
                            //     都登记进 basic_resource 才能访问。
                            auth.requestMatchers("/api/**").authenticated();
                        }
                        // 5) 兜底：其余接口只要登录即可（static 已在上方放行）
                        auth.anyRequest().authenticated();
                    } else {
                        // 灰度期：保持既有行为，全部放行
                        auth.anyRequest().permitAll();
                    }
                })
                // 未认证 / 无权限的响应格式：前端 axios 拦截器按 HTTP 状态码分支，
                // 返回 JSON 而不是默认的 HTML 错误页，避免前端 JSON.parse 失败
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpStatus.UNAUTHORIZED, 401,
                                        "未登录或登录已失效，请重新登录"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpStatus.FORBIDDEN, 403,
                                        "无权限访问该资源"))
                );

        return http.build();
    }

    /** 统一输出 {@link Result} 结构的 JSON 错误响应 */
    private void writeJson(jakarta.servlet.http.HttpServletResponse response,
                           HttpStatus status, int code, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // 手写 JSON 而不用注入 ObjectMapper：
        // Boot 4 把 Jackson 3（tools.jackson.*）作为首选，Jackson 2 仅为兼容保留，
        // 注入 ObjectMapper 的包名会随版本变化。这里只有两个固定字段，
        // 手写可以彻底躲开这个坑，也少一次依赖。
        // message 是代码里的常量字符串，不含用户输入，无需转义；
        // 若将来要带入动态内容，必须先做 JSON 转义再用。
        response.getWriter().write(
                "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
