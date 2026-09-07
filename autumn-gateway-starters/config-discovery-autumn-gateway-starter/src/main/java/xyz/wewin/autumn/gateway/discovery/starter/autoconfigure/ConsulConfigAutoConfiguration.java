package xyz.wewin.autumn.gateway.discovery.starter.autoconfigure;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

        private static final Logger log = LoggerFactory.getLogger(ConsulDiscoveryConfiguration.class);

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

            String host = resolveRegisterHost(env, properties.getHostname());

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

        /**
         * 解析服务注册用的 host，优先级：
         * <ol>
         *     <li>显式配置的 hostname（consul.discovery.hostname / spring.cloud.client.hostname，非回环时生效）；</li>
         *     <li>自动探测本地网卡上的非回环 IPv4 地址；</li>
         *     <li>探测失败时兜底为 {@code 127.0.0.1}。</li>
         * </ol>
         * 避免属性缺失或主机名被解析到回环地址时，服务以 127.0.0.1 注册导致其他节点无法访问。
         */
        private static String resolveRegisterHost(Environment env, String configuredHostname) {
            if (configuredHostname != null && !configuredHostname.isBlank() && !isLoopback(configuredHostname)) {
                return configuredHostname;
            }
            String host = env.getProperty("spring.cloud.client.hostname");
            if (host != null && !host.isBlank() && !isLoopback(host)) {
                return host;
            }
            String detected = detectNonLoopbackIpv4();
            if (detected != null) {
                log.info("使用本地网卡地址注册：{}", detected);
                return detected;
            }
            log.warn("未探测到可用的非回环 IPv4 地址，服务将以 127.0.0.1 注册");
            return "127.0.0.1";
        }

        private static boolean isLoopback(String host) {
            return "127.0.0.1".equals(host) || "::1".equals(host) || "localhost".equalsIgnoreCase(host);
        }

        /**
         * 遍历本地网卡，探测第一个可用的非回环 IPv4 地址（即本地网卡地址）。
         * 优先选择私网/站点本地地址（如 192.168.x.x、10.x.x.x），保证多网卡环境下结果稳定。
         */
        private static String detectNonLoopbackIpv4() {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                List<Inet4Address> candidates = new ArrayList<>();
                while (interfaces != null && interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                        continue;
                    }
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address inet4
                                && !addr.isLoopbackAddress()
                                && !addr.isLinkLocalAddress()
                                && !addr.isAnyLocalAddress()
                                && !addr.isMulticastAddress()) {
                            candidates.add(inet4);
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    return null;
                }
                candidates.sort(Comparator
                        .comparingInt((Inet4Address a) -> a.isSiteLocalAddress() ? 0 : 1)
                        .thenComparing(InetAddress::getHostAddress));
                return candidates.get(0).getHostAddress();
            } catch (SocketException e) {
                log.warn("获取本地网卡信息失败：{}", e.getMessage());
                return null;
            }
        }
    }
}