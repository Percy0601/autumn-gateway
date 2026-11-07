package xyz.wewin.autumn.gateway.thrift.support;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;
import xyz.wewin.autumn.gateway.thrift.autoconfigure.ThriftGenericProperties;
import xyz.wewin.autumn.protocol.generic.IdlParser;
import xyz.wewin.autumn.protocol.generic.ServiceRegistry;
import xyz.wewin.autumn.protocol.generic.ServiceSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 网关侧的 thrift 服务目录：IDL 解析结果 + 每个服务的连接信息。
 *
 * <p>启动时把 {@code autumn.thrift.services[*]} 里的 IDL 全部解析进
 * {@link ServiceRegistry}，解析失败直接抛出带服务名的异常——宁可启动失败，
 * 也不要带着半份描述表跑到线上才炸。</p>
 *
 * <p>{@link #register} 是给 P2 控制面（配置中心下发 IDL 后热注册）预留的入口，
 * 现在只有启动期调用。</p>
 */
public final class ThriftServiceCatalog {

    private final ServiceRegistry registry = new ServiceRegistry();

    private final Map<String, ThriftEndpoint> endpoints = new LinkedHashMap<>();

    private final ResourceLoader resourceLoader;

    ThriftServiceCatalog(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /** 按配置装配服务目录；任何一份 IDL 有问题都在这里失败。 */
    public static ThriftServiceCatalog load(ThriftGenericProperties properties, ResourceLoader resourceLoader) {
        ThriftServiceCatalog catalog = new ThriftServiceCatalog(resourceLoader);
        List<ThriftGenericProperties.Service> services = properties.getServices();
        if (services == null || services.isEmpty()) {
            return catalog;
        }
        for (ThriftGenericProperties.Service service : services) {
            catalog.register(service, properties);
        }
        return catalog;
    }

    /** 解析一份 IDL 并登记服务（P2 的热注册入口）。 */
    public synchronized ServiceSpec register(ThriftGenericProperties.Service service,
                                             ThriftGenericProperties defaults) {
        String idl = readIdl(service);
        List<ServiceSpec> specs = parse(service, idl);
        ServiceSpec target = select(service, specs);

        boolean framed = service.getFramed() != null ? service.getFramed() : defaults.isFramed();
        boolean multiplexed = service.getMultiplexed() != null
                ? service.getMultiplexed() : defaults.isMultiplexed();
        String multiplexedName = StringUtils.hasText(service.getMultiplexedName())
                ? service.getMultiplexedName().trim() : target.name();

        this.registry.register(target);
        this.endpoints.put(target.name(), new ThriftEndpoint(
                service.getHost(),
                service.getPort(),
                framed,
                multiplexed,
                multiplexedName));
        return target;
    }

    public Optional<ThriftEndpoint> endpoint(String serviceName) {
        return Optional.ofNullable(this.endpoints.get(serviceName));
    }

    public ServiceRegistry registry() {
        return this.registry;
    }

    public ServiceSpec require(String serviceName) {
        return this.registry.require(serviceName);
    }

    public ServiceSpec get(String serviceName) {
        return this.registry.get(serviceName);
    }

    public Set<String> serviceNames() {
        return Set.copyOf(this.endpoints.keySet());
    }

    public Map<String, ThriftEndpoint> endpoints() {
        return Map.copyOf(this.endpoints);
    }

    private List<ServiceSpec> parse(ThriftGenericProperties.Service service, String idl) {
        try {
            List<ServiceSpec> parsed = IdlParser.parseAll(idl);
            if (parsed.isEmpty()) {
                throw new IllegalStateException("IDL 里没有任何 service 定义：" + describe(service));
            }
            return parsed;
        }
        catch (IllegalArgumentException e) {
            throw new IllegalStateException("IDL 解析失败（" + describe(service) + "）：" + e.getMessage(), e);
        }
    }

    private ServiceSpec select(ThriftGenericProperties.Service service, List<ServiceSpec> specs) {
        if (!StringUtils.hasText(service.getName())) {
            if (specs.size() != 1) {
                throw new IllegalStateException("未配置 autumn.thrift.services[].name，但 IDL 里有 "
                        + specs.size() + " 个 service，请显式指定：" + names(specs));
            }
            return specs.get(0);
        }
        String name = service.getName().trim();
        for (ServiceSpec spec : specs) {
            if (spec.name().equals(name)) {
                return spec;
            }
        }
        throw new IllegalStateException("配置的服务名 " + name + " 不在 "
                + describe(service) + " 的 IDL 中，可选：" + names(specs));
    }

    private String readIdl(ThriftGenericProperties.Service service) {
        if (StringUtils.hasText(service.getIdl())) {
            return service.getIdl();
        }
        if (!StringUtils.hasText(service.getIdlLocation())) {
            throw new IllegalStateException("未配置 IDL：" + describe(service)
                    + "，请设置 autumn.thrift.services[].idl 或 .idl-location");
        }
        Resource resource = this.resourceLoader.getResource(service.getIdlLocation().trim());
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("读取 IDL 失败：" + service.getIdlLocation() + "（"
                    + describe(service) + "）", e);
        }
    }

    private static String names(List<ServiceSpec> specs) {
        List<String> names = new ArrayList<>(specs.size());
        for (ServiceSpec spec : specs) {
            names.add(spec.name());
        }
        return String.join(", ", names);
    }

    private static String describe(ThriftGenericProperties.Service service) {
        String name = StringUtils.hasText(service.getName()) ? service.getName() : "<未命名>";
        return name + " -> " + service.getHost() + ":" + service.getPort();
    }

    /** 一个后端 thrift 端点及其编解码形态。 */
    public record ThriftEndpoint(String host, int port, boolean framed, boolean multiplexed,
                                 String multiplexedName) {
    }
}
