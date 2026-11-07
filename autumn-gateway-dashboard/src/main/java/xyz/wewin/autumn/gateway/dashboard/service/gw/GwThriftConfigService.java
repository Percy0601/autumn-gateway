package xyz.wewin.autumn.gateway.dashboard.service.gw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwThriftConversion;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwThriftService;
import xyz.wewin.autumn.gateway.dashboard.repo.GwThriftConversionRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwThriftServiceRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 协议转换（HTTP → Thrift 泛化调用）配置导出。
 *
 * <p>把 gw_thrift_service / gw_thrift_conversion 落库的内容拼成网关可直接绑定的
 * {@code autumn.thrift.*} properties，供「配置同步」页与配置中心一起下发。
 * 对应关系（参照 Apache ShenYu 的 HTTP→Dubbo 三段式）：</p>
 * <ul>
 *   <li>ShenYu 的 selector/rule 匹配（URI/Header/Query/Cookie） ≈ 我们的 SCG 路由 Predicate；</li>
 *   <li>ShenYu 的 metadata（serviceName/methodName/parameterTypes） ≈ {@code autumn.thrift.services}；</li>
 *   <li>ShenYu 的 RpcParamTransformPlugin + param-mapping ≈ {@code autumn.thrift.mappings}。</li>
 * </ul>
 */
@Service
public class GwThriftConfigService {

    private final GwThriftServiceRepository serviceRepo;
    private final GwThriftConversionRepository conversionRepo;

    @Autowired
    public GwThriftConfigService(GwThriftServiceRepository serviceRepo,
                                 GwThriftConversionRepository conversionRepo) {
        this.serviceRepo = serviceRepo;
        this.conversionRepo = conversionRepo;
    }

    /** 生成 autumn.thrift.* 配置块；没有可用配置时返回空列表（不影响现有导出内容）。 */
    public List<String> exportLines() {
        List<GwThriftService> services = new ArrayList<>(serviceRepo.findAllEnabled());
        if (services.isEmpty()) {
            return List.of();
        }
        List<GwThriftConversion> conversions = new ArrayList<>(conversionRepo.findAllEnabled());

        List<String> lines = new ArrayList<>();
        lines.add("# ============================================================");
        lines.add("# 协议转换：HTTP endpoint → Thrift 泛化调用（gw_thrift_service / gw_thrift_conversion）");
        lines.add("# 路由上写 ThriftGeneric=<service>,<method>,<conversion_code> 引用下面的 mappings");
        lines.add("# ============================================================");
        lines.add("autumn.thrift.enabled=true");

        int i = 0;
        for (GwThriftService s : services) {
            String base = "autumn.thrift.services[" + i + "]";
            // name 必须等于 IDL 里的 service 名：路由过滤器和 attachment 都按它查服务目录
            lines.add(base + ".name=" + s.getServiceCode());
            lines.add(base + ".host=" + s.getHost());
            lines.add(base + ".port=" + (s.getPort() == null ? 0 : s.getPort()));
            if (StringUtils.hasText(s.getIdlLocation())) {
                lines.add(base + ".idl-location=" + s.getIdlLocation().trim());
            }
            if (StringUtils.hasText(s.getIdlContent())) {
                lines.add(base + ".idl=" + inline(s.getIdlContent()));
            }
            lines.add(base + ".framed=" + (s.getFramed() == null || s.getFramed() == 1));
            lines.add(base + ".multiplexed=" + (s.getMultiplexed() != null && s.getMultiplexed() == 1));
            if (s.getMultiplexed() != null && s.getMultiplexed() == 1 && StringUtils.hasText(s.getMultiplexedName())) {
                lines.add(base + ".multiplexed-name=" + s.getMultiplexedName());
            }
            i++;
        }

        int m = 0;
        for (GwThriftConversion c : conversions) {
            String base = "autumn.thrift.mappings[" + m + "]";
            lines.add(base + ".name=" + c.getConversionCode());
            lines.add(base + ".include-body=" + (c.getIncludeBody() == null || c.getIncludeBody() == 1));

            List<Map<String, Object>> rules = parseRules(c.getArgumentsJson());
            int a = 0;
            for (Map<String, Object> rule : rules) {
                String ab = base + ".arguments[" + a + "]";
                lines.add(ab + ".arg=" + str(rule.get("arg")));
                lines.add(ab + ".source=" + (StringUtils.hasText(str(rule.get("source"))) ? str(rule.get("source")) : "BODY"));
                if (StringUtils.hasText(str(rule.get("key")))) {
                    lines.add(ab + ".key=" + str(rule.get("key")));
                }
                if (StringUtils.hasText(str(rule.get("value"))) && "FIXED".equalsIgnoreCase(str(rule.get("source")))) {
                    lines.add(ab + ".value=" + str(rule.get("value")));
                }
                if (Boolean.parseBoolean(str(rule.get("required")))) {
                    lines.add(ab + ".required=true");
                }
                if (StringUtils.hasText(str(rule.get("defaultValue"))) || StringUtils.hasText(str(rule.get("default-value"))) ) {
                    String dv = StringUtils.hasText(str(rule.get("defaultValue"))) ? str(rule.get("defaultValue")) : str(rule.get("default-value"));
                    lines.add(ab + ".default-value=" + dv);
                }
                a++;
            }

            String mode = StringUtils.hasText(c.getResponseMode()) ? c.getResponseMode().toUpperCase() : "ENVELOPE";
            lines.add(base + ".response.mode=" + mode);
            if (c.getSuccessStatus() != null && c.getSuccessStatus() > 0) {
                lines.add(base + ".response.success-status=" + c.getSuccessStatus());
            }
            m++;
        }
        return lines;
    }

    /** 预览：{counts, text}，供页面「预览」按钮使用。 */
    public Map<String, Object> preview() {
        List<String> lines = exportLines();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("thriftServices", (int) serviceRepo.findAllEnabled().size());
        counts.put("thriftConversions", (int) conversionRepo.findAllEnabled().size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("text", String.join("\n", lines));
        out.put("counts", counts);
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRules(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
            if (parsed instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
            return List.of();
        }
        catch (Exception ignored) {
            return List.of();
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** properties 单行值：换行转义成 \n，避免多行 IDL 破坏文件结构 */
    private static String inline(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n");
    }
}
