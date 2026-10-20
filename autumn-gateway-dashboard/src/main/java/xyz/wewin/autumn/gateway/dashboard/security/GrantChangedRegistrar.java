package xyz.wewin.autumn.gateway.dashboard.security;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 {@link GrantChanged} 的拦截器。
 *
 * <p>用 Spring 自带的 {@link AnnotationMatchingPointcut} + {@link DefaultPointcutAdvisor}
 * 实现「按注解织入」，底层是 Spring 核心的代理机制（{@code aopalliance} 接口随
 * spring-core 提供），<b>不需要额外的 AspectJ starter</b>。</p>
 *
 * <p>只对标注了 {@link GrantChanged} 的方法生效，因此不会误伤
 * {@code setTitle} 这类普通实体 setter——这一点比按方法名通配的切面更安全。</p>
 *
 * <p>代理方式：Boot 默认 {@code spring.aop.proxy-target-class=true}（CGLIB），
 * 按具体类型注入不受影响；若有人把它改成 {@code false}，需注意
 * {@code BasicRoleService} 等类是按类注入的，届时会启动失败——启动即报错，
 * 不会留成运行期隐患。</p>
 */
@Configuration
public class GrantChangedRegistrar {

    @Bean
    public Advisor grantChangedAdvisor(PermissionService permissionService) {
        AnnotationMatchingPointcut pointcut =
                new AnnotationMatchingPointcut(null, GrantChanged.class, true);
        return new DefaultPointcutAdvisor(pointcut, new GrantChangedInterceptor(permissionService));
    }
}
