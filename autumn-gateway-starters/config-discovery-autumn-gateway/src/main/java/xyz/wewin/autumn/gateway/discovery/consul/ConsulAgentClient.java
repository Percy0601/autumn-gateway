package xyz.wewin.autumn.gateway.discovery.consul;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsulAgentClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;
    private final String acl;
    private final HttpClient http;

    public ConsulAgentClient(String scheme, String host, int port, String token, String acl, Duration connectTimeout) {
        this.baseUrl = scheme + "://" + host + ":" + port;
        this.token = token;
        this.acl = acl;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    public void register(ServiceRegistration registration) throws IOException {
        String json = buildRegisterJson(registration);
        HttpResponse<String> response = put("/v1/agent/service/register", json, Duration.ofSeconds(5));
        if (response.statusCode() != 200) {
            throw new IOException("consul register failed http " + response.statusCode() + ": " + response.body());
        }
    }

    private String buildRegisterJson(ServiceRegistration r) {
        var root = MAPPER.createObjectNode();
        root.put("Name", r.Name());
        root.put("ID", r.ID());
        root.put("Address", r.Address());
        root.put("Port", r.Port());
        root.put("EnableTagOverride", r.EnableTagOverride());

        if (r.Tags() != null && !r.Tags().isEmpty()) {
            var tagsNode = root.putArray("Tags");
            for (String tag : r.Tags()) {
                tagsNode.add(tag);
            }
        }

        if (r.Meta() != null && !r.Meta().isEmpty()) {
            var metaNode = root.putObject("Meta");
            for (var e : r.Meta().entrySet()) {
                metaNode.put(e.getKey(), e.getValue());
            }
        }

        if (r.Check() != null && !r.Check().isEmpty()) {
            var checkNode = root.putObject("Check");
            for (var e : r.Check().entrySet()) {
                Object val = e.getValue();
                if (val instanceof String s) {
                    checkNode.put(e.getKey(), s);
                } else if (val instanceof Number n) {
                    checkNode.put(e.getKey(), n.doubleValue());
                } else if (val instanceof Boolean b) {
                    checkNode.put(e.getKey(), b);
                }
            }
        }

        return root.toString();
    }

    public void deregister(String instanceId) throws IOException {
        HttpResponse<String> response = put("/v1/agent/service/deregister/" + instanceId, "", Duration.ofSeconds(5));
        if (response.statusCode() != 200) {
            throw new IOException("consul deregister failed http " + response.statusCode() + ": " + response.body());
        }
    }

    public List<String> getServices() throws IOException {
        HttpResponse<String> response = get("/v1/catalog/services", Duration.ofSeconds(5));
        if (response.statusCode() == 404) {
            return List.of();
        }
        if (response.statusCode() != 200) {
            throw new IOException("consul catalog services http " + response.statusCode() + ": " + response.body());
        }
        List<String> services = new ArrayList<>();
        JsonNode root = MAPPER.readTree(response.body());
        root.fieldNames().forEachRemaining(services::add);
        return services;
    }

    public WatchResult watchServices(long index, Duration wait) throws IOException {
        HttpResponse<String> response = get("/v1/catalog/services?index=" + index
                + "&wait=" + Math.max(1, wait.toSeconds()) + "s", Duration.ofSeconds(120));
        long nextIndex = response.headers().firstValueAsLong("X-Consul-Index").orElse(index);
        if (response.statusCode() == 404) {
            return new WatchResult(List.of(), nextIndex);
        }
        if (response.statusCode() != 200) {
            throw new IOException("consul catalog services watch http " + response.statusCode() + ": " + response.body());
        }
        List<String> services = new ArrayList<>();
        JsonNode root = MAPPER.readTree(response.body());
        root.fieldNames().forEachRemaining(services::add);
        return new WatchResult(services, nextIndex);
    }

    public List<ServiceNode> getHealthyInstances(String serviceName) throws IOException {
        HttpResponse<String> response = get("/v1/health/service/" + serviceName + "?passing=true", Duration.ofSeconds(5));
        if (response.statusCode() == 404) {
            return List.of();
        }
        if (response.statusCode() != 200) {
            throw new IOException("consul health service http " + response.statusCode() + ": " + response.body());
        }
        return parseNodes(response.body());
    }

    public WatchResultNodes watchHealthyInstances(String serviceName, long index, Duration wait) throws IOException {
        HttpResponse<String> response = get("/v1/health/service/" + serviceName + "?passing=true&index=" + index
                + "&wait=" + Math.max(1, wait.toSeconds()) + "s", Duration.ofSeconds(120));
        long nextIndex = response.headers().firstValueAsLong("X-Consul-Index").orElse(index);
        if (response.statusCode() == 404) {
            return new WatchResultNodes(List.of(), nextIndex);
        }
        if (response.statusCode() != 200) {
            throw new IOException("consul health watch http " + response.statusCode() + ": " + response.body());
        }
        return new WatchResultNodes(parseNodes(response.body()), nextIndex);
    }

    private List<ServiceNode> parseNodes(String body) throws IOException {
        List<ServiceNode> nodes = new ArrayList<>();
        JsonNode array = MAPPER.readTree(body);
        for (JsonNode node : array) {
            JsonNode service = node.get("Service");
            if (service == null) continue;

            String serviceId = pathText(service, "Name", "");
            String instanceId = pathText(service, "ID", serviceId);
            String host = pathText(service, "Address", "");
            int port = pathInt(service, "Port", 0);
            boolean secure = false;

            Map<String, String> metadata = new HashMap<>();
            JsonNode metaNode = service.get("Meta");
            if (metaNode != null && metaNode.isObject()) {
                metaNode.fields().forEachRemaining(e -> metadata.put(e.getKey(), e.getValue().asText()));
            }

            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = service.get("Tags");
            if (tagsNode != null && tagsNode.isArray()) {
                for (JsonNode t : tagsNode) {
                    tags.add(t.asText());
                }
            }

            if (host.isEmpty()) {
                JsonNode agent = node.get("Node");
                if (agent != null) {
                    host = pathText(agent, "Address", host);
                }
            }

            nodes.add(new ServiceNode(serviceId, instanceId, host, port, secure, metadata, tags));
        }
        return nodes;
    }

    private String pathText(JsonNode node, String field, String defaultValue) {
        JsonNode v = node.get(field);
        return (v != null && !v.isNull()) ? v.asText() : defaultValue;
    }

    private int pathInt(JsonNode node, String field, int defaultValue) {
        JsonNode v = node.get(field);
        return (v != null && !v.isNull()) ? v.asInt() : defaultValue;
    }

    private HttpResponse<String> get(String query, Duration requestTimeout) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + query))
                .timeout(requestTimeout)
                .GET();
        applyAuth(builder);
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("consul request interrupted", e);
        }
    }

    private HttpResponse<String> put(String path, String json, Duration requestTimeout) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json));
        applyAuth(builder);
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("consul request interrupted", e);
        }
    }

    private HttpResponse<String> delete(String path, Duration requestTimeout) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .method("DELETE", HttpRequest.BodyPublishers.noBody());
        applyAuth(builder);
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("consul request interrupted", e);
        }
    }

    private void applyAuth(HttpRequest.Builder builder) {
        if (token != null && !token.isBlank()) {
            builder.header("X-Consul-Token", token);
        }
        if (acl != null && !acl.isBlank()) {
            // acl is usually sent as token too, but keep separate for compatibility
            if (token == null || token.isBlank()) {
                builder.header("X-Consul-Token", acl);
            }
        }
    }

    public record ServiceRegistration(String Name, String ID, String Address, int Port, boolean EnableTagOverride,
                                       List<String> Tags, Map<String, String> Meta,
                                       Map<String, Object> Check) {
    }

    public record ServiceNode(String serviceId, String instanceId, String host, int port, boolean secure,
                              Map<String, String> metadata, List<String> tags) {
    }

    public record WatchResult(List<String> services, long index) {
    }

    public record WatchResultNodes(List<ServiceNode> nodes, long index) {
    }
}