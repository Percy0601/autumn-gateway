package xyz.wewin.autumn.gateway.dashboard.service.gw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwServiceInstance;
import xyz.wewin.autumn.gateway.dashboard.repo.GwServiceInstanceRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务治理：远程 invoke autumn service framework 的控制面 ControlApi，汇聚观测与度量数据。
 *
 * <p>调用链：页面 → 本服务 REST → {@link AutumnControlClient}（Thrift 二进制协议）
 * → 目标实例 30880 端口上注册的 {@code ControlApi$Iface}。</p>
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li>每次观测新建一条连接、用完即关：控制面是低频运维调用，不做连接池，避免长连接泄漏；</li>
 *   <li>单实例失败不影响其它实例：overview 逐个捕获异常，返回 reachable=false + error；</li>
 *   <li>控制面 token 不使用：框架侧默认不校验，optional 字段直接不发送，避免两边维护同一密钥；</li>
 *   <li>invoke 只允许 control.thrift 里登记的 5 个方法，防止方法名被滥用。</li>
 * </ul>
 */
@Service
public class GwGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(GwGovernanceService.class);

    private static final List<String> ALLOWED_METHODS = List.of(
            AutumnControlClient.METHOD_HEALTH,
            AutumnControlClient.METHOD_APPLICATION_INFO,
            AutumnControlClient.METHOD_PROVIDER_INFO,
            AutumnControlClient.METHOD_REFERENCE_CONFIG,
            AutumnControlClient.METHOD_CHECK);

    private final GwServiceInstanceRepository repo;
    private final int connectTimeoutMillis;
    private final int requestTimeoutMillis;

    public GwGovernanceService(GwServiceInstanceRepository repo,
                               @Value("${autumn.governance.connect-timeout-millis:1000}") int connectTimeoutMillis,
                               @Value("${autumn.governance.request-timeout-millis:3000}") int requestTimeoutMillis) {
        this.repo = repo;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    /** 控制面方法元数据，供前端 invoke 控制台渲染，避免前后端各维护一份方法清单 */
    public List<Map<String, Object>> methods() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(method(AutumnControlClient.METHOD_HEALTH, "存活探测", "返回 OK / DOWN，反映 Thrift 服务是否监听", List.of()));
        result.add(method(AutumnControlClient.METHOD_APPLICATION_INFO, "应用信息", "ApplicationConfig，含 JVM / OS 等运行时信息", List.of()));
        result.add(method(AutumnControlClient.METHOD_PROVIDER_INFO, "提供者与度量", "ProviderConfig + metrics 摘要（借用/成功/丢弃）", List.of()));
        result.add(method(AutumnControlClient.METHOD_REFERENCE_CONFIG, "引用配置", "查询指定服务的 consumer 连接池配置",
                List.of(param("name", "服务名", "string", true))));
        result.add(method(AutumnControlClient.METHOD_CHECK, "连通性探测", "让目标实例去探测另一个 ip:port 的 health",
                List.of(param("ip", "目标 IP", "string", true), param("port", "目标端口", "number", true))));
        return result;
    }

    /** 观测单个实例：health + applicationInfo + providerInfo，并解析出结构化度量 */
    public Map<String, Object> observe(GwServiceInstance instance) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instanceId", instance.getId());
        out.put("serviceName", instance.getServiceName());
        out.put("host", instance.getHost());
        out.put("port", instance.resolvePort());
        out.put("status", instance.getStatus());
        long start = System.currentTimeMillis();
        try (AutumnControlClient client = open(instance)) {
            String health = client.health();
            out.put("reachable", true);
            out.put("healthy", "OK".equals(health));
            out.put("health", health);
            out.put("applicationInfo", client.applicationInfo());
            String providerInfo = client.providerInfo();
            out.put("providerInfo", providerInfo);
            out.put("providerConfig", AutumnMetricsParser.parseProviderConfig(providerInfo));
            out.put("metrics", AutumnMetricsParser.parse(providerInfo));
        } catch (Exception e) {
            log.debug("observe {}:{} failed: {}", instance.getHost(), instance.resolvePort(), e.toString());
            out.put("reachable", false);
            out.put("healthy", false);
            out.put("error", rootMessage(e));
            out.put("metrics", List.of());
            out.put("providerConfig", Map.of());
        }
        out.put("elapsedMillis", System.currentTimeMillis() - start);
        return out;
    }

    /** 首屏 / 定时刷新：聚合所有启用实例的观测结果 */
    public List<Map<String, Object>> overview() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GwServiceInstance instance : repo.findAllEnabled()) {
            result.add(observe(instance));
        }
        return result;
    }

    /** 手动 invoke：返回控制面原始字符串，便于排查与验证接口契约 */
    public String invoke(GwServiceInstance instance, String method, Map<String, Object> params) {
        if (!ALLOWED_METHODS.contains(method)) {
            throw new IllegalArgumentException("不支持的控制面方法: " + method);
        }
        Map<String, Object> p = params == null ? Map.of() : params;
        try (AutumnControlClient client = open(instance)) {
            return switch (method) {
                case AutumnControlClient.METHOD_HEALTH -> client.health();
                case AutumnControlClient.METHOD_APPLICATION_INFO -> client.applicationInfo();
                case AutumnControlClient.METHOD_PROVIDER_INFO -> client.providerInfo();
                case AutumnControlClient.METHOD_REFERENCE_CONFIG -> client.referenceConfig(text(p.get("name")));
                case AutumnControlClient.METHOD_CHECK -> client.check(text(p.get("ip")), intValue(p.get("port")));
                default -> throw new IllegalArgumentException("不支持的控制面方法: " + method);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("远程 invoke 失败: " + rootMessage(e), e);
        }
    }

    private AutumnControlClient open(GwServiceInstance instance) throws Exception {
        if (instance.getHost() == null || instance.getHost().isBlank()) {
            throw new IllegalArgumentException("实例 host 不能为空");
        }
        return new AutumnControlClient(instance.getHost().trim(), instance.resolvePort(),
                connectTimeoutMillis, requestTimeoutMillis);
    }

    private static String rootMessage(Throwable e) {
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }

    private static String text(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static int intValue(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(text(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Map<String, Object> method(String name, String label, String description,
                                              List<Map<String, Object>> params) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("label", label);
        m.put("description", description);
        m.put("params", params);
        return m;
    }

    private static Map<String, Object> param(String key, String label, String type, boolean required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("type", type);
        m.put("required", required);
        return m;
    }
}
