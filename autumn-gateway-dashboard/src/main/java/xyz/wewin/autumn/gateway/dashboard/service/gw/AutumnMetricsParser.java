package xyz.wewin.autumn.gateway.dashboard.service.gw;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 {@code ControlApi.providerInfo()} 返回串里的度量摘要。
 *
 * <p>框架侧 AutumnMetrics.summary() 的输出形如：</p>
 * <pre>
 *   ProviderConfig(ip=127.0.0.1, port=30880, ...), metrics={svcA={borrowed=12, succeeded=11, discarded=1}, svcB={...}}
 * </pre>
 *
 * <p>borrowed = 连接池借用次数，succeeded = 成功归还次数，discarded = 异常丢弃次数。
 * 这里只做宽松解析：字段缺失、格式变化、服务名为空都退化成空列表，绝不让观测接口因解析失败而 500。</p>
 */
public final class AutumnMetricsParser {

    private static final String METRICS_MARKER = "metrics=";

    /** 单个服务条目：{borrowed=N, succeeded=N, discarded=N} */
    private static final Pattern ENTRY = Pattern.compile(
            "([^{}=\\s,]+)\\s*=\\s*\\{\\s*borrowed=(\\d+)\\s*,\\s*succeeded=(\\d+)\\s*,\\s*discarded=(\\d+)\\s*\\}");

    /** ProviderConfig(...) / ApplicationConfig(...) 里的 key=value 片段 */
    private static final Pattern KV = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)=([^,)]*)");

    private AutumnMetricsParser() {
    }

    /** 解析 providerInfo，返回按 borrowed 降序的服务度量列表 */
    public static List<ServiceMetric> parse(String providerInfo) {
        String body = extractMetricsBody(providerInfo);
        if (body.isEmpty()) {
            return List.of();
        }
        List<ServiceMetric> result = new ArrayList<>();
        Matcher m = ENTRY.matcher(body);
        while (m.find()) {
            result.add(new ServiceMetric(m.group(1),
                    parseLong(m.group(2)), parseLong(m.group(3)), parseLong(m.group(4))));
        }
        result.sort((a, b) -> Long.compare(b.borrowed(), a.borrowed()));
        return result;
    }

    /** providerInfo 前半段（ ProviderConfig(...) ）拆成宽松的 key → value，供页面展示 */
    public static Map<String, String> parseProviderConfig(String providerInfo) {
        Map<String, String> map = new LinkedHashMap<>();
        if (providerInfo == null || providerInfo.isBlank()) {
            return map;
        }
        int idx = providerInfo.lastIndexOf(METRICS_MARKER);
        String head = idx > 0 ? providerInfo.substring(0, idx) : providerInfo;
        Matcher m = KV.matcher(head);
        while (m.find()) {
            map.putIfAbsent(m.group(1), m.group(2).trim());
        }
        return map;
    }

    /** 截取 metrics={...} 的花括号内容，用括号配对避免被嵌套的 {} 截断 */
    private static String extractMetricsBody(String providerInfo) {
        if (providerInfo == null || providerInfo.isBlank()) {
            return "";
        }
        int marker = providerInfo.lastIndexOf(METRICS_MARKER);
        if (marker < 0) {
            return "";
        }
        String tail = providerInfo.substring(marker + METRICS_MARKER.length());
        int open = tail.indexOf('{');
        if (open < 0) {
            return "";
        }
        int depth = 0;
        for (int i = open; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return tail.substring(open + 1, i);
            }
        }
        // 没有闭合括号也尽量解析，避免丢数据
        return tail.substring(open + 1);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 服务维度度量。successRate / discardRate 以 borrowed 为分母，返回 0~100 的百分数 */
    public record ServiceMetric(String service, long borrowed, long succeeded, long discarded) {
        public double successRate() {
            return borrowed == 0 ? 0 : round(succeeded * 100.0 / borrowed);
        }

        public double discardRate() {
            return borrowed == 0 ? 0 : round(discarded * 100.0 / borrowed);
        }

        private static double round(double v) {
            return Math.round(v * 100.0) / 100.0;
        }
    }
}
