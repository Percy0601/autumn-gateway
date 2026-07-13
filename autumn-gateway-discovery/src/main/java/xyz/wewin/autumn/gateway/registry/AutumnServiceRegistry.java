package xyz.wewin.autumn.gateway.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

public class AutumnServiceRegistry implements ServiceRegistry<AutumnRegistration> {
    private Logger log = LoggerFactory.getLogger(this.getClass());
    private final AutumnRegistryClient client; // 你自己的注册中心 HTTP 客户端

    public AutumnServiceRegistry(AutumnRegistryClient client) {
        this.client = client;
    }

    @Override
    public void register(AutumnRegistration reg) {
        // 对应 Consul 的 PUT /v1/agent/service/register
        client.register(reg);
        log.info("registered: {} / {}", reg.getServiceId(), reg.getInstanceId());
    }

    @Override
    public void deregister(AutumnRegistration reg) {
        // 对应 Consul 的 DELETE /v1/agent/service/deregister/{id}
        client.deregister(reg.getInstanceId());
        log.info("deregistered: {}", reg.getInstanceId());
    }

    @Override
    public void close() {
        // 可选：JVM 关的时候批量摘，Consul 那边是靠 ShutdownHook + SmartLifecycle.stop() 双保险


    }

    @Override
    public void setStatus(AutumnRegistration registration, String status) {
        registration.setStatus(status);
    }

    @Override
    public <T> T getStatus(AutumnRegistration registration) {
        return (T)registration.getStatus();
    }
}