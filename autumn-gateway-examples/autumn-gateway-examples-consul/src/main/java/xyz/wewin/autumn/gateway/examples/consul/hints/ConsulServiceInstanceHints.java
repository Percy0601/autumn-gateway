package xyz.wewin.autumn.gateway.examples.consul.hints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.consul.discovery.ConsulDiscoveryClient;
import org.springframework.cloud.consul.discovery.ConsulServiceInstance;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.ecwid.consul.v1.health.model.HealthService;
import com.ecwid.consul.v1.health.model.Check;

/**
 * 不生效，使用录制
 */
@Component
@ImportRuntimeHints(ConsulServiceInstanceHints.class)
public class ConsulServiceInstanceHints implements RuntimeHintsRegistrar {
    private static final Logger log = LoggerFactory.getLogger(ConsulServiceInstanceHints.class);

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader cl) {
        log.info("Registering Consul native-image runtime hints");

        // ===== Spring Cloud Bootstrap 机制所需 =====
//        hints.reflection().registerType(
//                org.springframework.cloud.bootstrap.RefreshBootstrapRegistryInitializer.class,
//                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
//                        MemberCategory.ACCESS_DECLARED_FIELDS,
//                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
//        );
//        hints.reflection().registerType(
//                org.springframework.cloud.bootstrap.BootstrapApplicationListener.class,
//                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
//                        MemberCategory.ACCESS_DECLARED_FIELDS,
//                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
//        );
//        hints.reflection().registerType(
//                org.springframework.cloud.bootstrap.TextEncryptorConfigBootstrapper.class,
//                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
//                        MemberCategory.ACCESS_DECLARED_FIELDS,
//                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
//        );

        // ===== ServiceInstance 接口 + 实现类 =====
        hints.reflection().registerType(ServiceInstance.class,
                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
        );

        hints.reflection().registerType(ConsulServiceInstance.class,
                b -> b.withMembers(
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
                )
        );

        hints.reflection().registerType(ConsulDiscoveryClient.class,
                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
        );

        // ===== Consul 客户端模型类 =====
        hints.reflection().registerType(HealthService.class,
                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
        ));

        hints.reflection().registerType(CatalogService.class,
                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS
        ));

        hints.reflection().registerType(Check.class,
                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
        ));

        hints.reflection().registerType(HealthService.Node.class,
                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
        ));

        hints.reflection().registerType(HealthService.Service.class,
                b -> b.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
        ));

        hints.proxies().registerJdkProxy(
                org.springframework.cloud.client.discovery.DiscoveryClient.class
        );

        hints.resources().registerPattern("META-INF/spring.factories");
        hints.resources().registerPattern("META-INF/spring/*");
    }
}
