package xyz.wewin.autumn.gateway.dashboard.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「会改变授权结果」的方法，方法正常返回后自动清空权限缓存。
 *
 * <pre>{@code
 * @GrantChanged
 * public void setMenus(Long roleId, List<Long> menuIds) { ... }
 * }</pre>
 *
 * <p><b>和 {@code @Transactional} 一样是声明式的</b>，比在方法体里手写
 * {@code invalidator.publish()} 更不容易漏、也更好搜（全局搜索注解就能
 * 列出所有授权变更入口，评审时一眼看完）。</p>
 *
 * <p>只有正常返回才清缓存：抛异常说明事务回滚了，缓存本来就不该动。</p>
 *
 * <p>实现见 {@link GrantChangedInterceptor}，注册见 {@code WebMvcConfig}。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GrantChanged {
}
