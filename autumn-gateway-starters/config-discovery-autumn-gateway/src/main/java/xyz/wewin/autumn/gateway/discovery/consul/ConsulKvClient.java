package xyz.wewin.autumn.gateway.discovery.consul;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consul KV HTTP API 极简客户端。
 *
 * <p>实现要点（面向轻量 / GraalVM Native）：
 * <ul>
 *   <li>HTTP 使用 JDK 自带 {@link HttpClient}，零第三方 HTTP 依赖；</li>
 *   <li>JSON 仅用 {@link JsonNode} 字段遍历解码，不使用反射绑定，天然兼容 Native Image；</li>
 *   <li>支持 Consul Blocking Query（{@code ?index=..&wait=..}）长轮询，作为配置变更检测基础。</li>
 * </ul>
 */
public class ConsulKvClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;
    private final HttpClient http;

    public ConsulKvClient(String scheme, String host, int port, String token, Duration connectTimeout) {
        this.baseUrl = scheme + "://" + host + ":" + port + "/v1/kv";
        this.token = token;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    /** 读取某前缀下的整棵 KV 子树（递归）。子树不存在时返回空列表。 */
    public List<ConsulKvEntry> list(String keyPrefix) throws IOException {
        return toEntries(get(keyPrefix + "?recurse=true", Duration.ofSeconds(10)));
    }

    /**
     * Consul Blocking Query：阻塞至多 {@code wait} 秒，等待该前缀下配置变化。
     *
     * @return 返回最新条目与下一次使用的 index（无变化时 entries 为空、index 不变）
     */
    public WatchResult watch(String keyPrefix, long index, Duration wait) throws IOException {
        HttpResponse<String> response = get(keyPrefix
                + "?recurse=true&index=" + index
                + "&wait=" + Math.max(1, wait.toSeconds()) + "s", Duration.ofSeconds(120));
        long nextIndex = response.headers().firstValueAsLong("X-Consul-Index").orElse(index);
        return new WatchResult(toEntries(response), nextIndex);
    }

    private HttpResponse<String> get(String query, Duration requestTimeout) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + query))
                .timeout(requestTimeout)
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("X-Consul-Token", token);
        }
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("consul kv request interrupted", e);
        }
    }

    private List<ConsulKvEntry> toEntries(HttpResponse<String> response) throws IOException {
        if (response.statusCode() == 404) {
            return List.of(); // 子树不存在
        }
        if (response.statusCode() != 200) {
            throw new IOException("consul kv http " + response.statusCode() + ": " + response.body());
        }
        List<ConsulKvEntry> entries = new ArrayList<>();
        JsonNode array = MAPPER.readTree(response.body());
        for (JsonNode node : array) {
            String key = node.path("Key").asText();
            JsonNode value = node.get("Value");
            if (key.isBlank() || value == null || value.isNull()) {
                continue; // 目录占位节点（无值）
            }
            byte[] decoded = Base64.getDecoder().decode(value.asText());
            entries.add(new ConsulKvEntry(key, new String(decoded, StandardCharsets.UTF_8)));
        }
        return entries;
    }

    /** 按 "/" 层级编码 key（保留层级分隔，转义非法字符） */
    static String encodePath(String key) {
        return Arrays.stream(key.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
    }

    public record ConsulKvEntry(String key, String value) {
    }

    public record WatchResult(List<ConsulKvEntry> entries, long index) {
    }
}
