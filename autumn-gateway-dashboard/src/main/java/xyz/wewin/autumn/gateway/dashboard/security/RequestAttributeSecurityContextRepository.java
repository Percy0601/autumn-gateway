package xyz.wewin.autumn.gateway.dashboard.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 请求级别（不落 Session / Cookie）的 {@link SecurityContextRepository}。
 *
 * <h2>为什么必须显式注册</h2>
 * <p>纯 JWT 无状态服务若不给 {@code SecurityContextHolderFilter} 指定仓库，
 * Spring Security 会退化成 {@code NullSecurityContextRepository}。
 * 它的 {@code loadDeferredContext} 走接口默认实现，每次求值都
 * {@code new HttpRequestResponseHolder} 再 {@code loadContext()}，
 * 拿到的是<b>全新的空 context</b>。holder 里装的就是这个「每次求值都不同」的
 * Supplier，于是 JWT 过滤器写入的认证态在下游读取时被新生成的空 context 顶掉
 * —— 外部表现为「登录成功，但带 token 访问任何接口都 401」。</p>
 *
 * <h2>实现要点</h2>
 * <ol>
 *   <li>context 存在 request attribute 上，<b>首次访问即创建</b>，
 *       同一请求内反复取到同一个实例，写入必然可见；</li>
 *   <li>生命周期绑定 request，请求结束随请求销毁，不跨请求保留，符合无状态要求；</li>
 *   <li>{@link #loadDeferredContext} 返回的 deferred 对象<b>不调用
 *       {@code SecurityContextHolder.getContext()}</b> —— 这是关键。
 *       {@code SecurityContextHolderFilter} 会把它放进 holder，之后 holder 的每次
 *       求值都会回到这个对象；若在 {@code get()} 里再回头读 holder，
 *       就形成 get → supplier → get 的无限递归（StackOverflowError）。</li>
 * </ol>
 *
 * <p>注册方式见 {@code SecurityConfig}：通过
 * {@code SecurityContextConfigurer#securityContextRepository(...)} 注入，
 * 它内部是 {@code setSharedObject}，因此<b>必须写在
 * {@code authorizeHttpRequests(...)} 之前</b>，
 * 否则 {@code AuthorizeHttpRequestsConfigurer} 在 init 阶段读不到它，
 * 会退回默认仓库（症状与完全不配置一样）。这个顺序坑本项目已经踩过一次。</p>
 */
public class RequestAttributeSecurityContextRepository implements SecurityContextRepository {

    /** request attribute 的 key；加类名前缀避免与业务属性撞名 */
    public static final String ATTR_NAME =
            RequestAttributeSecurityContextRepository.class.getName() + ".SPRING_SECURITY_CONTEXT";
    private static final String ATTR = ATTR_NAME;

    private final SecurityContextHolderStrategy holderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    @Override
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        return load(requestResponseHolder.getRequest());
    }

    @Override
    public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        final SecurityContext ctx = load(request);
        // 直接返回构建时捕获的实例：holder 每次求值拿到的都是同一个 context，
        // JWT 过滤器对它的写入在整条链上都可见。
        return new FixedDeferredSecurityContext(ctx);
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request,
                            HttpServletResponse response) {
        // 无状态：不写 Cookie / Session，仅保留在 request 生命周期内。
        // deferred 模式下框架不会主动调到这里；显式调用（如登录成功时
        // 需要在同一请求内复用认证态）也能正确落位。
        if (context != null) {
            request.setAttribute(ATTR, context);
        }
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return request.getAttribute(ATTR) instanceof SecurityContext;
    }

    /** 取同一请求内共享的 context；首次访问时创建并挂到 request 上 */
    private SecurityContext load(HttpServletRequest request) {
        if (request.getAttribute(ATTR) instanceof SecurityContext existing) {
            return existing;
        }
        SecurityContext ctx = holderStrategy.createEmptyContext();
        request.setAttribute(ATTR, ctx);
        return ctx;
    }

    /**
     * 固定实例的 deferred 包装：{@link #get()} 直接返回构造时持有的 context。
     *
     * <p>绝不能在这里调 {@code SecurityContextHolder.getContext()} ——
     * 本对象可能正是 holder 的 Supplier，回头读 holder 会无限递归。</p>
     */
    private record FixedDeferredSecurityContext(SecurityContext context)
            implements DeferredSecurityContext {

        @Override
        public SecurityContext get() {
            return this.context;
        }

        @Override
        public boolean isGenerated() {
            // 相比持久化仓库，这里每个请求的 context 都是新生、未持久化的
            return true;
        }
    }
}
