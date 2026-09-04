package xyz.wewin.autumn.gateway.examples.consul.hints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

/**
 *
 * @author: baoxin.zhao
 * @date: 7/4/26
 */
@Component
@ImportRuntimeHints(ConsulServiceInstanceHints.class)
public class ConsulServiceInstanceHints implements RuntimeHintsRegistrar {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader cl) {
        // ===== 1. ServiceInstance 接口 —— 本次 MissingReflectionRegistrationError 根因 =====
        // DiscoveryClientRouteDefinitionLocator 的 SpEL root object 是 ServiceInstance（接口类型）
        // ReflectivePropertyAccessor 反射读 getServiceId() 时，接口方法必须进 hints
        hints.reflection().registerType(ServiceInstance.class,
                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS)
        );

        // ===== 2. ConsulServiceInstance —— 实现类，SpEL 可能 also 走实现类路径 =====
        hints.reflection().registerType(
                org.springframework.cloud.consul.discovery.ConsulServiceInstance.class,
                b -> b.withMembers(
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS
                )
        );

        // ===== 3. ConsulDiscoveryClient —— 注册阶段 / HealthIndicator =====
        hints.reflection().registerType(
                org.springframework.cloud.consul.discovery.ConsulDiscoveryClient.class,
                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
        );
    }
}
