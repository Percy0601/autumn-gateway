package xyz.wewin.autumn.gateway.examples.discovery.controller;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示服务发现的用法：暴露两个接口查看注册中心里的服务。
 *
 * <p>底层用的就是 {@link AutumnDiscoveryClient}（示例里为伪代码，返回空列表，
 * 接入真实注册中心后即可看到数据）。</p>
 *
 * @author: autumn-gateway
 */
@RestController
public class DiscoveryController {

    private final DiscoveryClient discoveryClient;

    public DiscoveryController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    /**
     * GET /discovery/services → 注册中心上所有服务名
     */
    @GetMapping("/discovery/services")
    public List<String> services() {
        return discoveryClient.getServices();
    }

    /**
     * GET /discovery/instances/{serviceId} → 某个服务的实例列表
     */
    @GetMapping("/discovery/instances/{serviceId}")
    public List<Map<String, Object>> instances(@PathVariable String serviceId) {
        return discoveryClient.getInstances(serviceId).stream().map(this::toMap).toList();
    }

    /**
     * GET /discovery/round-robin/{serviceId} → 模拟网关 LoadBalancer 选一个实例
     */
    @GetMapping("/discovery/round-robin/{serviceId}")
    public Map<String, Object> pick(@PathVariable String serviceId) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        if (instances.isEmpty()) {
            return Map.of("error", "no available instance for service: " + serviceId);
        }
        // 伪代码：真实网关用 LoadBalancerClient / ReactorLoadBalancer 做轮询，这里简单取第一个
        return toMap(instances.get(0));
    }

    private Map<String, Object> toMap(ServiceInstance instance) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("serviceId", instance.getServiceId());
        map.put("instanceId", instance.getInstanceId());
        map.put("host", instance.getHost());
        map.put("port", instance.getPort());
        map.put("uri", instance.getUri().toString());
        map.put("metadata", instance.getMetadata());
        return map;
    }
}
