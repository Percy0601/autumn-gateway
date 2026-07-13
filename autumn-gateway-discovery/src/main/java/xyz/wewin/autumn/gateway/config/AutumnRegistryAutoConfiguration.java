package xyz.wewin.autumn.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import xyz.wewin.autumn.gateway.registry.AutumnAutoServiceRegistration;
import xyz.wewin.autumn.gateway.registry.AutumnRegistration;
import xyz.wewin.autumn.gateway.registry.AutumnRegistryClient;
import xyz.wewin.autumn.gateway.registry.AutumnServiceRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@AutoConfiguration
@ConditionalOnDiscoveryEnabled
@ConditionalOnProperty(value = "spring.cloud.autumn-registry.enabled", matchIfMissing = true)
public class AutumnRegistryAutoConfiguration {

    @Bean
    public AutumnRegistryClient autumnRegistryClient(
            @Value("${spring.cloud.my-registry.server:http://localhost:9999}") String server) {
        return new AutumnRegistryClient(server);
    }

    @Primary
    @Bean
    public AutumnServiceRegistry autumnServiceRegistry(AutumnRegistryClient client) {
        return new AutumnServiceRegistry(client);
    }

    @Bean
    public AutumnRegistration autumnRegistration(
            Environment env,
            @Value("${server.port:8080}") int port) {
        String app = env.getProperty("spring.application.name", "unknown");
        String host = env.getProperty("spring.cloud.client.hostname", "127.0.0.1");
        return new AutumnRegistration(
                app,
                app + "-" + port + "-" + UUID.randomUUID(), // instanceId
                host,
                port,
                "true",
                List.of("v1"),
                Map.of("weight", "1")
        );
    }

    @Bean
    public AutumnAutoServiceRegistration autumnAutoServiceRegistration(
            ApplicationContext context,
            AutumnServiceRegistry autumnServiceRegistry,
            AutumnRegistration autumnRegistration) {
        return new AutumnAutoServiceRegistration(context, autumnServiceRegistry, autumnRegistration);
    }

}
