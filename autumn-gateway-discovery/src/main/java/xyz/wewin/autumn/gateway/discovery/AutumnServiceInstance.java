package xyz.wewin.autumn.gateway.discovery;

import org.jspecify.annotations.Nullable;
import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 服务发现返回的实例信息，对应 Spring Cloud Consul 的 {@code ConsulServiceInstance}。
 *
 * <p>从注册中心拉到的实例原始数据（host / port / metadata 等）都会装进这个对象，
 * 网关的 {@code DiscoveryClientRouteDefinitionLocator} 和 {@code LoadBalancerClient}
 * 最终消费的就是 {@link ServiceInstance}。</p>
 *
 * <p>{@link #getUri()} 走 {@link ServiceInstance} 接口的 default 实现：
 * {@code isSecure() ? "https://host:port" : "http://host:port"}，所以这里不用自己拼。</p>
 *
 * @author: autumn-gateway
 */
public class AutumnServiceInstance implements ServiceInstance {

    private final String serviceId;
    private final String instanceId;
    private final String host;
    private final int port;
    private final boolean secure;
    private final Map<String, String> metadata;
    private final List<String> tags;

    public AutumnServiceInstance(String serviceId, String instanceId, String host, int port,
                                 boolean secure, Map<String, String> metadata, List<String> tags) {
        this.serviceId = serviceId;
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.secure = secure;
        this.metadata = metadata;
        this.tags = tags;
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public @Nullable String getInstanceId() {
        return instanceId;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public List<String> getTags() {
        return tags;
    }

    @Override
    public URI getUri() {
        // ServiceInstance 的 default 实现，等价于 URI.create((secure ? "https" : "http") + "://" + host + ":" + port)
        // return ServiceInstance.super.getUri();
        // ServiceInstance 接口的 default getUri() 已经拼好了 scheme://host:port，
        // 返回的就是当前这个服务实例节点的访问地址（URL），不是注册中心地址。
        // 这里不能写成 ServiceInstance.super.getUri()：因为 ServiceInstance 的 default 方法
        // 会调用 this.getHost() / this.getPort() / this.isSecure()，而这些是抽象方法，
        // 直接 super 调用会在运行时因找不到具体实现而报错（或依赖接口默认返回空值）。
        return URI.create((secure ? "https" : "http") + "://" + host + ":" + port);
    }

    @Override
    public String toString() {
        return "AutumnServiceInstance{" +
                "serviceId='" + serviceId + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", secure=" + secure +
                ", metadata=" + metadata +
                ", tags=" + tags +
                '}';
    }
}
