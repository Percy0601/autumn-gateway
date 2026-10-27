package xyz.wewin.autumn.gateway.dashboard.service.gw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigCenter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 注册中心客户端：v1 只实现 Consul，复用项目里 ConsulAgentClient 同款 HTTP 调用，
 * 不引 spring-cloud-consul 依赖。Nacos 后续加一个分支即可。
 */
@Service
public class GwDiscoveryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    /** 拉取某配置中心下注册的所有服务名 */
    public List<String> listServices(GwConfigCenter center) {
        if (!"CONSUL".equalsIgnoreCase(center.getCenterType())) {
            throw new IllegalArgumentException("暂不支持的配置中心类型: " + center.getCenterType());
        }
        String url = String.format("http://%s:%d/v1/catalog/services", center.getHost(), center.getPort());
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(5));
            if (center.getAclToken() != null && !center.getAclToken().isBlank()) {
                b.header("X-Consul-Token", center.getAclToken());
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("consul http " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode root = MAPPER.readTree(resp.body());
            List<String> services = new ArrayList<>();
            root.fieldNames().forEachRemaining(name -> {
                if (!name.startsWith("") && !name.equals("")) services.add(name);
            });
            services.sort(String::compareTo);
            return services;
        } catch (Exception e) {
            throw new RuntimeException("拉取服务列表失败: " + e.getMessage(), e);
        }
    }
}
