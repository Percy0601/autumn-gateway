package xyz.wewin.autumn.gateway.discovery.consul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import xyz.wewin.autumn.gateway.discovery.config.ConsulDiscoveryProperties;

public class ConsulServiceRegistry implements ServiceRegistry<Registration> {

    private static final Logger log = LoggerFactory.getLogger(ConsulServiceRegistry.class);

    private final ConsulAgentClient client;
    private final ConsulDiscoveryProperties properties;

    public ConsulServiceRegistry(ConsulAgentClient client, ConsulDiscoveryProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void register(Registration registration) {
        if (!(registration instanceof ConsulRegistration consulReg)) {
            log.warn("Expected ConsulRegistration but got {}", registration.getClass());
            return;
        }
        try {
            String healthCheckUrl = null;
            if (properties.getHealthCheckPath() != null) {
                String scheme = properties.getHealthCheckProtocol() != null
                        ? properties.getHealthCheckProtocol()
                        : (consulReg.isSecure() ? "https" : "http");
                healthCheckUrl = scheme + "://" + consulReg.getHost() + ":" + consulReg.getPort()
                        + properties.getHealthCheckPath();
            }
            String interval = properties.getHealthCheckInterval().toSeconds() + "s";
            String timeout = properties.getHealthCheckTimeout().toSeconds() + "s";

            ConsulAgentClient.ServiceRegistration svcReg = consulReg.toServiceRegistration(
                    healthCheckUrl, interval, timeout);
            client.register(svcReg);
            log.info("Registered service [{}] instanceId=[{}] at {}:{}",
                    consulReg.getServiceId(), consulReg.getInstanceId(),
                    consulReg.getHost(), consulReg.getPort());
        } catch (Exception e) {
            log.error("Failed to register service [{}]: {}", consulReg.getServiceId(), e.getMessage(), e);
        }
    }

    @Override
    public void deregister(Registration registration) {
        if (registration == null || registration.getInstanceId() == null) {
            return;
        }
        try {
            client.deregister(registration.getInstanceId());
            log.info("Deregistered service instanceId=[{}]", registration.getInstanceId());
        } catch (Exception e) {
            log.error("Failed to deregister instanceId=[{}]: {}", registration.getInstanceId(), e.getMessage(), e);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void setStatus(Registration registration, String status) {
        // Consul 通过 catalog API 设置 status，这里简化处理
        log.debug("setStatus called (no-op): {} -> {}", registration.getServiceId(), status);
    }

    @Override
    public <T> T getStatus(Registration registration) {
        return null;
    }
}