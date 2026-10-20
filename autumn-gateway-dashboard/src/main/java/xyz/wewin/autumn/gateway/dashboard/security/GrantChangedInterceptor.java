package xyz.wewin.autumn.gateway.dashboard.security;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link GrantChanged} 的实现：在方法正常返回后清空权限缓存。
 *
 * <p>只实现「清缓存」这一件事，所以直接手写 {@code MethodInterceptor}，
 * 不依赖 {@code spring-boot-starter-aspectj}（Boot 4 已把它改名，且
 * 为一个清 Map 的动作引入字节码织入并不划算）。
 * 注册方式见 {@link GrantChangedRegistrar}。</p>
 */
public class GrantChangedInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrantChangedInterceptor.class);

    private final PermissionService permissionService;

    public GrantChangedInterceptor(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 先执行原方法；抛异常会直接向上冒泡，不走下面的清缓存逻辑
        Object result = invocation.proceed();
        permissionService.evictAll();
        log.debug("授权变更 {} → 已清空权限缓存",
                invocation.getMethod().getDeclaringClass().getSimpleName()
                        + "#" + invocation.getMethod().getName());
        return result;
    }
}
