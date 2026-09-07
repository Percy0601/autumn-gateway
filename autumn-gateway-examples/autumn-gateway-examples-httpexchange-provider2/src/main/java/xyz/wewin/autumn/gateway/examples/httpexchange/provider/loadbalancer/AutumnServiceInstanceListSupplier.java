package xyz.wewin.autumn.gateway.examples.httpexchange.provider.loadbalancer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义 LoadBalancer 实例列表供应商：把自定义服务发现（{@code AutumnDiscoveryClient}）与
 * LoadBalancer 桥接起来，并在选实例前做过滤 + 兜底。
 *
 * <p>调用链：</p>
 * <pre>
 * ReactorLoadBalancer（默认 RoundRobinLoadBalancer）
 *   → ServiceInstanceListSupplier.get()
 *       → AutumnServiceInstanceListSupplier（本类）
 *           → super.get()
 *               → DiscoveryClientServiceInstanceListSupplier
 *                   → AutumnDiscoveryClient.getInstances(serviceId)（自定义服务发现）
 * </pre>
 *
 * <p>两个扩展点：</p>
 * <ul>
 *     <li>规则1：metadata["health"] 为 down / unhealthy 的实例剔除 —— 模拟 Consul 的 passing 语义</li>
 *     <li>规则2：metadata["weight"] 为 0 的实例剔除 —— 权重为 0 不参与流量</li>
 *     <li>兜底：DiscoveryClient 查不到实例时，回退到
 *         {@code spring.cloud.autumn-registry.static-instances.<serviceId>} 配置的静态实例，
 *         保证注册中心（教学伪代码）不工作时负载均衡依然可演示</li>
 * </ul>
 *
 * @author: autumn-gateway
 */
public class AutumnServiceInstanceListSupplier extends DiscoveryClientServiceInstanceListSupplier {

    private static final Log log = LogFactory.getLog(AutumnServiceInstanceListSupplier.class);

    private static final String STATIC_INSTANCES_PREFIX = "spring.cloud.autumn-registry.static-instances.";

    private final Environment environment;

    public AutumnServiceInstanceListSupplier(DiscoveryClient delegate, Environment environment) {
        super(delegate, environment);
        this.environment = environment;
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return super.get().map(this::filterRoutableInstances);
    }

    private List<ServiceInstance> filterRoutableInstances(List<ServiceInstance> instances) {
        List<ServiceInstance> filtered = instances.stream()
                .filter(this::isRoutable)
                .toList();

        // 兜底：自定义注册中心为伪代码（返回空列表）时，改用配置的静态实例，演示负载均衡轮询
        if (filtered.isEmpty()) {
            List<ServiceInstance> staticInstances = loadStaticInstances();
            if (!staticInstances.isEmpty()) {
                log.info("AutumnServiceInstanceListSupplier: no instance from discovery for service ["
                        + getServiceId() + "], fallback to static instances: "
                        + staticInstances.stream().map(i -> i.getHost() + ":" + i.getPort()).toList());
                return staticInstances;
            }
        }

        if (filtered.size() != instances.size()) {
            log.info("AutumnServiceInstanceListSupplier filtered out "
                    + (instances.size() - filtered.size())
                    + " instance(s) from " + instances.size() + " for service [" + getServiceId() + "]");
        }
        return filtered;
    }

    /**
     * 从 {@code spring.cloud.autumn-registry.static-instances.<serviceId>[i]=host:port} 读取静态实例。
     */
    private List<ServiceInstance> loadStaticInstances() {
        String serviceId = getServiceId();
        List<ServiceInstance> instances = new ArrayList<>();
        for (int i = 0; ; i++) {
            String entry = environment.getProperty(STATIC_INSTANCES_PREFIX + serviceId + "[" + i + "]");
            if (entry == null || entry.isBlank()) {
                break;
            }
            String[] hostPort = entry.split(":");
            if (hostPort.length != 2) {
                continue;
            }
            instances.add(new DefaultServiceInstance(
                    serviceId + "-static-" + i, serviceId, hostPort[0].trim(), Integer.parseInt(hostPort[1].trim()), false));
        }
        return instances;
    }

    /**
     * 伪代码：真实的健康判断应来自注册中心（如 Consul 的 health check 状态），
     * 这里用 metadata 模拟，方便演示过滤逻辑。
     */
    private boolean isRoutable(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata == null) {
            return true;
        }
        // 规则1：模拟 Consul passing 语义——健康状态不通过的实例不参与流量
        String health = metadata.get("health");
        if ("down".equalsIgnoreCase(health) || "unhealthy".equalsIgnoreCase(health)) {
            return false;
        }
        // 规则2：权重为 0 的实例不参与流量
        String weight = metadata.get("weight");
        return !"0".equals(weight);
    }
}
