package xyz.wewin.autumn.gateway.registry;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.wewin.autumn.gateway.discovery.AutumnServiceInstance;

import java.util.List;

public class AutumnRegistryClient {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    private String server;

    public AutumnRegistryClient(String server) {
        this.server = server;
    }

    // ==================== 服务注册（写） ====================

    public void register(AutumnRegistration registration) {
        log.info("client begin registry service: {}", registration);
    }

    public void deregister(@Nullable String instanceId) {
        log.info("client begin deregister service: {}", instanceId);
    }

    // ==================== 服务发现（读） ====================

    /**
     * 查询某个服务下的实例列表。
     * 对应 Consul 的 GET /v1/health/service/{serviceName}?passing=true
     *
     * <p>伪代码：真实实现时用 HTTP 调用注册中心，把返回的 JSON 反序列化成
     * {@link AutumnServiceInstance} 列表（只保留 passing 的实例）。</p>
     */
    public List<AutumnServiceInstance> findInstances(String serviceId) {
        log.info("client discover instances for service [{}] from server: {}", serviceId, server);
        return List.of();
    }

    /**
     * 查询注册中心上所有服务名。
     * 对应 Consul 的 GET /v1/catalog/services
     *
     * <p>伪代码：真实实现时解析响应 JSON 的 key 集合返回。</p>
     */
    public List<String> findAllServices() {
        log.info("client discover all services from server: {}", server);
        return List.of();
    }
}
