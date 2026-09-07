package xyz.wewin.autumn.gateway.examples.discovery.loadbalancer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 自定义 LoadBalancer 实例列表供应商，演示 LoadBalancer 与服务发现整合的扩展点。
 *
 * <p>说明：Spring Cloud Consul 实际上<strong>没有</strong>单独的 ListSupplier 类——
 * 它是在 {@code ConsulDiscoveryClient.getInstances()} 里通过 {@code ?passing=true}
 * 只返回健康实例（对应本项目 {@code AutumnDiscoveryClient} 注释里的语义）。
 * 这里把"健康过滤"搬到 LoadBalancer 层，做一个对等的教学示例：
 * 继承 {@link DiscoveryClientServiceInstanceListSupplier}（默认的 LB→DiscoveryClient 桥），
 * 重写 {@link #get()} 在 LoadBalancer 选实例之前做二次过滤。</p>
 *
 * <p>调用链：</p>
 * <pre>
 * ReactorLoadBalancer（默认 RoundRobinLoadBalancer）
 *   → ServiceInstanceListSupplier.get()
 *       → AutumnServiceInstanceListSupplier（本类，过滤）
 *           → super.get()
 *               → DiscoveryClientServiceInstanceListSupplier
 *                   → AutumnDiscoveryClient.getInstances(serviceId)
 * </pre>
 *
 * <p>过滤规则（演示两种，按注册实例 metadata 判断）：</p>
 * <ul>
 *     <li>规则1：metadata["health"] 为 down / unhealthy 的实例剔除 —— 模拟 Consul 的 passing 语义</li>
 *     <li>规则2：metadata["weight"] 为 0 的实例剔除 —— 权重为 0 不参与流量</li>
 * </ul>
 *
 * @author: autumn-gateway
 */
public class AutumnServiceInstanceListSupplier extends DiscoveryClientServiceInstanceListSupplier {

    private static final Log log = LogFactory.getLog(AutumnServiceInstanceListSupplier.class);

    private final DiscoveryClient delegate;

    public AutumnServiceInstanceListSupplier(DiscoveryClient delegate, Environment environment) {
        super(delegate, environment);
        this.delegate = delegate;
    }

    /**
     * super.get() 内部就是 {@code delegate.getInstances(serviceId)}（对应我们自己的
     * {@code AutumnDiscoveryClient}），这里在它基础上做实例过滤。
     */
    @Override
    public Flux<List<ServiceInstance>> get() {
        return super.get().map(this::filterRoutableInstances);
    }

    private List<ServiceInstance> filterRoutableInstances(List<ServiceInstance> instances) {
        List<ServiceInstance> filtered = instances.stream()
                .filter(this::isRoutable)
                .toList();
        if (filtered.size() != instances.size()) {
            log.info("AutumnServiceInstanceListSupplier filtered out "
                    + (instances.size() - filtered.size())
                    + " instance(s) from " + instances.size() + " for service [" + getServiceId() + "]");
        }
        return filtered;
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
