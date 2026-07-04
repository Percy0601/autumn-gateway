package xyz.wewin.autumn.gateway.examples.consul.hints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

/**
 *
 * @author: baoxin.zhao
 * @date: 7/4/26
 */
@Component
@ImportRuntimeHints(ConsulServiceInstanceHints.class)
public class ConsulServiceInstanceHints implements RuntimeHintsRegistrar {
    private Logger log = LoggerFactory.getLogger(this.getClass());
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader cl) {
        // ConsulServiceInstance - SpEL 读 serviceId/instanceId/port 等 getter
        hints.reflection().registerType(
                org.springframework.cloud.consul.discovery.ConsulServiceInstance.class,
                b -> b.withMembers(
                        MemberCategory.INVOKE_PUBLIC_METHODS,   // getServiceId/getInstanceId/getPort/getAddress...
                        MemberCategory.ACCESS_DECLARED_FIELDS// 如果 SpEL 走字段也兜底
                )
        );

        // ConsulService - 如果 locator 里还读了 ConsulService (tags/address 等)
        // 看你 SCG 版本，5.0.2 的 DiscoveryClientRouteDefinitionLocator 内部可能还会过 ConsulService
        // 保险起见一起补
        try {
            Class<?> consulService = cl.loadClass("org.springframework.cloud.consul.discovery.ConsulService");
            hints.reflection()
                    .registerType(consulService, b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS))
                    .registerType(consulService, b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
        } catch (ClassNotFoundException e) {
            // 忽略，某些 consul 版本可能类名略有差异
            log.warn("Config Consul Hints, Error:", e);
        }
    }
}
