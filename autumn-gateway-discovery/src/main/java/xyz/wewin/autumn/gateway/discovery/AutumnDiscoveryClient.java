package xyz.wewin.autumn.gateway.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import xyz.wewin.autumn.gateway.registry.AutumnRegistryClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务发现客户端，对应 Spring Cloud Consul 的 {@code ConsulDiscoveryClient}。
 *
 * <p>这是服务发现的核心入口：Spring Cloud Gateway 启动时，
 * {@code DiscoveryClientRouteDefinitionLocator} 会调用 {@link #getServices()} 拿到全部服务名，
 * 再对每个服务调 {@link #getInstances(String)} 拿到实例列表，动态生成路由。</p>
 *
 * <p>对应 Consul 的 REST API：</p>
 * <ul>
 *     <li>{@link #getServices()}      → {@code GET /v1/catalog/services}</li>
 *     <li>{@link #getInstances(String)} → {@code GET /v1/health/service/{serviceName}?passing=true}</li>
 * </ul>
 *
 * @author: autumn-gateway
 */
public class AutumnDiscoveryClient implements DiscoveryClient {

    public static final String DESCRIPTION = "Autumn Discovery Client";

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final AutumnRegistryClient client;

    public AutumnDiscoveryClient(AutumnRegistryClient client) {
        this.client = client;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    /**
     * 查询某个服务下的健康实例列表。
     *
     * <p>Consul 侧默认带 {@code ?passing=true}，即只返回健康检查通过的实例；
     * 网关拿到这批实例后，交给 LoadBalancer（如 RoundRobin）挑一个去转发。</p>
     *
     * <p>伪代码：目前 {@link AutumnRegistryClient#findInstances} 只打日志返回空列表，
     * 真实实现时在这里发 HTTP 请求，把 JSON 反序列化成 {@link AutumnServiceInstance} 列表。</p>
     */
    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        // 对应 Consul 的 GET /v1/health/service/{serviceName}?passing=true
        List<AutumnServiceInstance> instances = client.findInstances(serviceId);

        // 伪代码：这里可以做二次过滤/排序，比如
        //   1. 过滤掉 metadata 里 weight=0 的实例（权重为 0 不参与流量）
        //   2. 按权重降序，让 LoadBalancer 的 Weighted 策略直接用
        log.info("discovered {} instance(s) for service [{}]: {}", instances.size(), serviceId, instances);

        return new ArrayList<>(instances);
    }

    /**
     * 查询注册中心上所有已注册的服务名（网关动态路由的第一步）。
     *
     * <p>伪代码：目前 {@link AutumnRegistryClient#findAllServices} 只打日志返回空列表，
     * 真实实现时调用 {@code GET /v1/catalog/services} 并解析出 key 集合。</p>
     */
    @Override
    public List<String> getServices() {
        // 对应 Consul 的 GET /v1/catalog/services
        List<String> services = client.findAllServices();
        log.info("discovered services: {}", services);
        return services;
    }

    /**
     * 多个 DiscoveryClient 并存时（如 Consul + Nacos + 自研）决定优先级，数字越小越优先。
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
