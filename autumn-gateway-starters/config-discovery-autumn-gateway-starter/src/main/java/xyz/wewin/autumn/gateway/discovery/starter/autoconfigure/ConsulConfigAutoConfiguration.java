package xyz.wewin.autumn.gateway.discovery.starter.autoconfigure;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import xyz.wewin.autumn.gateway.discovery.config.ConsulConfigProperties;
import xyz.wewin.autumn.gateway.discovery.config.ConsulConfigWatchRefresher;
import xyz.wewin.autumn.gateway.discovery.config.ConsulDiscoveryProperties;
import xyz.wewin.autumn.gateway.discovery.config.ConsulProperties;
import xyz.wewin.autumn.gateway.discovery.consul.ConsulAgentClient;
import xyz.wewin.autumn.gateway.discovery.consul.ConsulAutoServiceRegistration;
import xyz.wewin.autumn.gateway.discovery.consul.ConsulDiscoveryClient;
import xyz.wewin.autumn.gateway.discovery.consul.ConsulDiscoveryWatcher;
import xyz.wewin.autumn.gateway.discovery.consul.ConsulRegistration;
import xyz.wewin.autumn.gateway.discovery.consul.ConsulServiceRegistry;

@AutoConfiguration
@EnableConfigurationProperties({ConsulProperties.class, ConsulConfigProperties.class, ConsulDiscoveryProperties.class})
public class ConsulConfigAutoConfiguration {

    // ==================== 配置中心（独立，不依赖 Discovery 开关） ====================

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = ConsulConfigProperties.PREFIX, name = "watch-enabled",
            havingValue = "true", matchIfMissing = true)
    public ConsulConfigWatchRefresher consulConfigWatchRefresher(
            ConfigurableEnvironment environment,
            ConsulProperties consulProperties,
            ConsulConfigProperties configProperties,
            ApplicationEventPublisher publisher) {
        ConsulConfigWatchRefresher refresher =
                new ConsulConfigWatchRefresher(environment, consulProperties, configProperties, publisher);
        refresher.start();
        return refresher;
    }

    // ==================== Discovery / Registry ====================

    @Configuration
    @ConditionalOnDiscoveryEnabled
    @ConditionalOnProperty(prefix = ConsulDiscoveryProperties.PREFIX, name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class ConsulDiscoveryConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ConsulAgentClient consulAgentClient(ConsulProperties consulProperties) {
            return new ConsulAgentClient(
                    consulProperties.getScheme(),
                    consulProperties.getHost(),
                    consulProperties.getPort(),
                    consulProperties.getToken(),
                    null,
                    consulProperties.getTimeout()
            );
        }

        @Bean(destroyMethod = "stop")
        @ConditionalOnMissingBean
        @ConditionalOnBean(ConsulAgentClient.class)
        public ConsulDiscoveryWatcher consulDiscoveryWatcher(
                ConsulAgentClient consulAgentClient,
                ConsulDiscoveryProperties properties) {
            ConsulDiscoveryWatcher watcher = new ConsulDiscoveryWatcher(consulAgentClient, properties);
            watcher.start();
            return watcher;
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(ConsulAgentClient.class)
        public ConsulDiscoveryClient consulDiscoveryClient(
                ConsulAgentClient consulAgentClient,
                ConsulDiscoveryProperties properties,
                @Value("${consul.discovery.watch-enabled:true}") boolean watchEnabled,
                ConsulDiscoveryWatcher watcher) {
            ConsulDiscoveryWatcher watcherOrNull = (watchEnabled && properties.isWatchEnabled()) ? watcher : null;
            return new ConsulDiscoveryClient(consulAgentClient, watcherOrNull);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(ConsulAgentClient.class)
        @ConditionalOnProperty(prefix = ConsulDiscoveryProperties.PREFIX, name = "register",
                havingValue = "true", matchIfMissing = true)
        public ConsulServiceRegistry consulServiceRegistry(
                ConsulAgentClient consulAgentClient,
                ConsulDiscoveryProperties properties) {
            return new ConsulServiceRegistry(consulAgentClient, properties);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(ConsulServiceRegistry.class)
        @ConditionalOnProperty(prefix = ConsulDiscoveryProperties.PREFIX, name = "register",
                havingValue = "true", matchIfMissing = true)
        public Registration consulRegistration(
                Environment env,
                ConsulDiscoveryProperties properties,
                @Value("${server.port:8080}") int serverPort) {

            String serviceName = properties.getServiceName() != null
                    ? properties.getServiceName()
                    : env.getProperty("spring.application.name", "unknown");

            String host = properties.getHostname() != null
                    ? properties.getHostname()
                    : env.getProperty("spring.cloud.client.hostname", "127.0.0.1");

            int port = properties.getPortOverride() > 0
                    ? properties.getPortOverride()
                    : serverPort;

            String instanceId = properties.getInstanceId() != null
                    ? properties.getInstanceId()
                    : serviceName + "-" + port + "-" + UUID.randomUUID().toString().substring(0, 8);

            List<String> tags = properties.getTags() != null ? properties.getTags() : List.of();
            Map<String, String> metadata = properties.getMetadata() != null ? properties.getMetadata() : Map.of();

            return new ConsulRegistration(serviceName, instanceId, host, port, false, tags, metadata);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean({ConsulServiceRegistry.class, Registration.class})
        @ConditionalOnProperty(prefix = ConsulDiscoveryProperties.PREFIX, name = "register",
                havingValue = "true", matchIfMissing = true)
        public ConsulAutoServiceRegistration consulAutoServiceRegistration(
                ConsulServiceRegistry consulServiceRegistry,
                Registration registration) {
            return new ConsulAutoServiceRegistration(consulServiceRegistry, registration);
        }
    }
}