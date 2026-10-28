package xyz.wewin.autumn.gateway.dashboard.service.gw;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigCenter;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigItem;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigStruct;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwGlobalFilter;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwRoute;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwRouteFilter;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwRoutePredicate;
import xyz.wewin.autumn.gateway.dashboard.repo.GwConfigCenterRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwConfigItemRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwConfigStructRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwGlobalFilterRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwRouteFilterRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwRoutePredicateRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwRouteRepository;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配置同步：把网关可视化配置聚合为一份可被 SCG 5.0.3 直接绑定的 properties，
 * 支持预览 / 下载 / 推送到配置中心（Consul KV、Nacos 配置）。
 *
 * <p>前缀依据：spring-cloud-gateway-server-webflux-5.0.3-sources.jar 的
 * {@code GatewayProperties.PREFIX = "spring.cloud.gateway.server.webflux"}，
 * 与模板系统 v2 的 STRUCT 导出前缀（flattenStruct）一致。
 * 旧版 v1 种子里的 {@code spring.cloud.gateway.*}（4.x 前缀）在导出时自动归一化。</p>
 */
@Service
public class GwConfigSyncService {

    private static final String PREFIX = "spring.cloud.gateway.server.webflux";
    private static final ObjectMapper om = new ObjectMapper();

    private final GwRouteRepository routeRepo;
    private final GwRoutePredicateRepository predicateRepo;
    private final GwRouteFilterRepository routeFilterRepo;
    private final GwGlobalFilterRepository globalFilterRepo;
    private final GwConfigItemRepository configItemRepo;
    private final GwConfigCenterRepository centerRepo;
    private final GwConfigStructRepository structRepo;
    private final GwTemplateV2Service tplService;

    public GwConfigSyncService(GwRouteRepository routeRepo,
                               GwRoutePredicateRepository predicateRepo,
                               GwRouteFilterRepository routeFilterRepo,
                               GwGlobalFilterRepository globalFilterRepo,
                               GwConfigItemRepository configItemRepo,
                               GwConfigCenterRepository centerRepo,
                               GwConfigStructRepository structRepo,
                               GwTemplateV2Service tplService) {
        this.routeRepo = routeRepo;
        this.predicateRepo = predicateRepo;
        this.routeFilterRepo = routeFilterRepo;
        this.globalFilterRepo = globalFilterRepo;
        this.configItemRepo = configItemRepo;
        this.centerRepo = centerRepo;
        this.structRepo = structRepo;
        this.tplService = tplService;
    }

    /**
     * 聚合预览：{ profile, text, counts:{configItems,structs,globalFilters,routes,predicates,filters}, warn[] }
     */
    public Map<String, Object> preview(String profile) {
        String p = profile == null || profile.isBlank() ? "dev" : profile;
        List<String> lines = new ArrayList<>();
        List<String> warn = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        // ---------- 1. 网关全局配置项（gw_config_item） ----------
        List<GwConfigItem> items = new ArrayList<>(configItemRepo.findByProfile(p));
        items.sort(Comparator.comparing(GwConfigItem::getSortNum, Comparator.nullsLast(Comparator.naturalOrder())));
        lines.add("# ============================================================");
        lines.add("# 网关全局配置项（gw_config_item · profile=" + p + "）");
        lines.add("# ============================================================");
        int ci = 0;
        for (GwConfigItem it : items) {
            if (it.getIsEncrypted() != null && it.getIsEncrypted() == 1) {
                lines.add("# " + normalizePrefix(it.getConfigKey()) + "=<加密值，不导出>");
                continue;
            }
            String key = normalizePrefix(it.getConfigKey());
            if (!key.equals(it.getConfigKey())) {
                warn.add("配置项 " + it.getConfigKey() + " 已按 5.0.3 前缀归一化为 " + key);
            }
            lines.add(key + "=" + typedValue(it));
            ci++;
        }
        counts.put("configItems", ci);

        // ---------- 2. 结构化全局配置（gw_config_struct） ----------
        lines.add("");
        lines.add("# ============================================================");
        lines.add("# 结构化全局配置（gw_config_struct · profile=" + p + "）");
        lines.add("# ============================================================");
        int sc = 0;
        for (GwConfigStruct s : structRepo.findByProfileCodeAndDelFlagOrderBySortNum(p, 0)) {
            if (s.getStatus() != null && s.getStatus() == 0) {
                lines.add("# [停用] " + s.getInstanceName() + "（" + s.getStructName() + "）");
                continue;
            }
            lines.add("# --- 实例：" + s.getInstanceName() + "（" + s.getStructName() + "） ---");
            for (String row : tplService.structPreview(s.getId())) {
                if (row.startsWith("#")) lines.add(row);
                else lines.add(row);
            }
            sc++;
        }
        counts.put("structs", sc);

        // ---------- 3. 全局默认过滤器（default-filters） ----------
        List<GwGlobalFilter> globals = new ArrayList<>();
        globalFilterRepo.findAll().forEach(globals::add);
        globals.removeIf(g -> g.getDelFlag() != null && g.getDelFlag() == 1);
        globals.sort(Comparator.comparing(GwGlobalFilter::getSortNum, Comparator.nullsLast(Comparator.naturalOrder())));
        lines.add("");
        lines.add("# ============================================================");
        lines.add("# 全局默认过滤器（default-filters）");
        lines.add("# ============================================================");
        int gf = 0;
        int gi = 0;
        for (GwGlobalFilter g : globals) {
            if (g.getEnabled() == null || g.getEnabled() != 1) {
                lines.add("# [停用] " + g.getFilterName() + "（enabled=0）");
                continue;
            }
            String base = PREFIX + ".default-filters[" + gi + "]";
            lines.add(base + ".name=" + g.getFilterName());
            for (Map.Entry<String, String> e : flattenArgs("FILTER", g.getFilterName(), g.getArgsJson()).entrySet()) {
                lines.add(base + ".args." + e.getKey() + "=" + e.getValue());
            }
            gi++;
            gf++;
        }
        counts.put("globalFilters", gf);

        // ---------- 4. 路由（gw_route + predicates + filters） ----------
        List<GwRoute> routes = new ArrayList<>();
        routeRepo.findAll().forEach(routes::add);
        routes.removeIf(r -> r.getDelFlag() != null && r.getDelFlag() == 1);
        routes.sort(Comparator.comparing(GwRoute::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GwRoute::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        lines.add("");
        lines.add("# ============================================================");
        lines.add("# 路由（gw_route · profile 无关，全部启用/停用状态一并导出）");
        lines.add("# ============================================================");
        int pc = 0;
        int fc = 0;
        int ri = 0;
        for (GwRoute r : routes) {
            String base = PREFIX + ".routes[" + ri + "]";
            lines.add(base + ".id=" + (StringUtils.hasText(r.getRouteCode()) ? r.getRouteCode() : ("route-" + r.getId())));
            lines.add(base + ".uri=" + r.getUri());
            lines.add(base + ".order=" + (r.getOrderNum() == null ? 0 : r.getOrderNum()));
            lines.add(base + ".enabled=" + (r.getStatus() != null && r.getStatus() == 1));

            List<GwRoutePredicate> preds = new ArrayList<>(predicateRepo.findByRouteId(r.getId()));
            preds.removeIf(x -> x.getDelFlag() != null && x.getDelFlag() == 1);
            preds.sort(Comparator.comparing(GwRoutePredicate::getSortNum, Comparator.nullsLast(Comparator.naturalOrder())));
            int pj = 0;
            for (GwRoutePredicate pd : preds) {
                String pb = base + ".predicates[" + pj + "]";
                lines.add(pb + ".name=" + pd.getPredicateName());
                for (Map.Entry<String, String> e : flattenArgs("PREDICATE", pd.getPredicateName(), pd.getArgsJson()).entrySet()) {
                    lines.add(pb + ".args." + e.getKey() + "=" + e.getValue());
                }
                pj++;
                pc++;
            }

            List<GwRouteFilter> rfs = new ArrayList<>(routeFilterRepo.findByRouteId(r.getId()));
            rfs.removeIf(x -> x.getDelFlag() != null && x.getDelFlag() == 1);
            rfs.sort(Comparator.comparing(GwRouteFilter::getSortNum, Comparator.nullsLast(Comparator.naturalOrder())));
            int fj = 0;
            for (GwRouteFilter rf : rfs) {
                String fb = base + ".filters[" + fj + "]";
                lines.add(fb + ".name=" + rf.getFilterName());
                for (Map.Entry<String, String> e : flattenArgs("FILTER", rf.getFilterName(), rf.getArgsJson()).entrySet()) {
                    lines.add(fb + ".args." + e.getKey() + "=" + e.getValue());
                }
                fj++;
                fc++;
            }
            ri++;
        }
        counts.put("routes", routes.size());
        counts.put("predicates", pc);
        counts.put("filters", fc);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profile", p);
        out.put("text", String.join("\n", lines));
        out.put("counts", counts);
        out.put("warn", warn);
        return out;
    }

    /**
     * 推送到配置中心。返回 { centerId, centerName, type, target, success, message }。
     * CONSUL → KV PUT（namespace 作前缀，dataId=gateway/application-{profile}.properties）；
     * NACOS → POST /nacos/v1/cs/configs（dataId=application-{profile}.properties，group=DEFAULT_GROUP，tenant=namespace）。
     */
    public Map<String, Object> publish(String profile, Long centerId) {
        String p = profile == null || profile.isBlank() ? "dev" : profile;
        GwConfigCenter c = centerRepo.findById(centerId)
                .orElseThrow(() -> new IllegalArgumentException("配置中心不存在: " + centerId));
        String text = (String) preview(p).get("text");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("centerId", c.getId());
        out.put("centerName", c.getCenterName());
        out.put("type", c.getCenterType());
        try {
            if ("NACOS".equalsIgnoreCase(c.getCenterType())) {
                out.putAll(publishNacos(c, p, text));
            } else {
                out.putAll(publishConsul(c, p, text));
            }
        } catch (Exception e) {
            out.put("success", false);
            out.put("message", "推送失败: " + e.getMessage());
        }
        return out;
    }

    private Map<String, Object> publishConsul(GwConfigCenter c, String profile, String text) throws Exception {
        String kvKey = (StringUtils.hasText(c.getNamespace()) ? trimSlash(c.getNamespace()) + "/" : "")
                + "gateway/application-" + profile + ".properties";
        String target = "http://" + c.getHost() + ":" + c.getPort() + "/v1/kv/" + kvKey;
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "text/plain;charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8));
        if (StringUtils.hasText(c.getAclToken())) rb.header("X-Consul-Token", c.getAclToken());
        HttpResponse<String> resp = http().send(rb.build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("target", target);
        out.put("success", resp.statusCode() >= 200 && resp.statusCode() < 300);
        out.put("message", "Consul KV " + (out.get("success") == Boolean.TRUE ? "已写入" : "返回 " + resp.statusCode())
                + "（key=" + kvKey + "）");
        return out;
    }

    private Map<String, Object> publishNacos(GwConfigCenter c, String profile, String text) throws Exception {
        String dataId = "application-" + profile + ".properties";
        String group = StringUtils.hasText(c.getNamespace()) ? c.getNamespace() : "DEFAULT_GROUP";
        String target = "http://" + c.getHost() + ":" + c.getPort() + "/nacos/v1/cs/configs";
        String body = "dataId=" + enc(dataId) + "&group=" + enc(group)
                + "&tenant=" + enc(StringUtils.hasText(c.getNamespace()) ? c.getNamespace() : "")
                + "&content=" + enc(text);
        HttpRequest req = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
        String msg = resp.body() == null ? "" : resp.body().trim();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("target", target + "?dataId=" + dataId + "&group=" + group);
        out.put("success", (resp.statusCode() >= 200 && resp.statusCode() < 300) && "true".equalsIgnoreCase(msg));
        out.put("message", "Nacos 配置发布 " + (out.get("success") == Boolean.TRUE ? "成功" : "失败")
                + "（HTTP " + resp.statusCode() + "，响应: " + (msg.length() > 120 ? msg.substring(0, 120) : msg) + "）");
        return out;
    }

    // =====================================================================
    // 内部：args 扁平化 / 值类型 / 前缀归一化
    // =====================================================================

    /** 优先用 v2 模板导出 args（exportKey 扁平键）；模板不匹配时兜底 raw 扁平化 */
    private Map<String, String> flattenArgs(String kind, String tplName, String argsJson) {
        if (!StringUtils.hasText(tplName)) return Map.of();
        try {
            Map<String, Object> r = tplService.validateInstance(kind, tplName, argsJson == null ? "{}" : argsJson);
            if (Boolean.TRUE.equals(r.get("ok"))) {
                Object args = r.get("args");
                if (args instanceof Map) {
                    Map<String, String> out = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) args).entrySet()) {
                        out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                    }
                    return out;
                }
            }
        } catch (Exception ignored) {
            // 模板缺失或校验不过，走兜底
        }
        return flattenRaw(argsJson);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> flattenRaw(String argsJson) {
        Map<String, String> out = new LinkedHashMap<>();
        Object value;
        try {
            value = om.readValue(argsJson == null || argsJson.isBlank() ? "{}" : argsJson,
                    new TypeReference<Object>() {});
        } catch (Exception e) {
            return out;
        }
        if (value instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                flattenRawSlot(String.valueOf(e.getKey()), e.getValue(), out);
            }
        }
        return out;
    }

    private void flattenRawSlot(String path, Object v, Map<String, String> out) {
        if (v == null) return;
        if (v instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                flattenRawSlot(path + "." + e.getKey(), e.getValue(), out);
            }
            return;
        }
        if (v instanceof List) {
            List<String> items = new ArrayList<>();
            for (Object o : (List<?>) v) items.add(String.valueOf(o));
            out.put(path, String.join(",", items));
            return;
        }
        out.put(path, String.valueOf(v));
    }

    /** LIST/JSON 原样导出；BOOL/INT 等保持原值字符串；其他按文本 */
    private String typedValue(GwConfigItem it) {
        String v = it.getConfigValue();
        if (v == null) return "";
        String t = it.getValueType() == null ? "" : it.getValueType().toUpperCase();
        if ("BOOL".equals(t) || "INT".equals(t) || "LONG".equals(t) || "DOUBLE".equals(t)
                || "LIST".equals(t) || "JSON".equals(t)) {
            return v;
        }
        return v;
    }

    /** 旧 4.x 前缀 spring.cloud.gateway.* → 5.0.3 的 spring.cloud.gateway.server.webflux.* */
    private String normalizePrefix(String key) {
        if (key != null && key.startsWith("spring.cloud.gateway.")
                && !key.startsWith("spring.cloud.gateway.server.webflux.")) {
            return "spring.cloud.gateway.server.webflux." + key.substring("spring.cloud.gateway.".length());
        }
        return key;
    }

    private static String trimSlash(String s) {
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static HttpClient http() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
}
