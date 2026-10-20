package xyz.wewin.autumn.gateway.dashboard.security;

/**
 * 一条 URL 级权限资源，对应 {@code basic_resource} 的一行。
 *
 * @param id         basic_resource.id
 * @param name       资源名，仅用于日志
 * @param path       URL 模式，如 {@code /api/system/basic/user}
 * @param httpMethod 支持多值逗号分隔，如 {@code GET,POST}；{@code ALL} 表示全部
 * @param matchType  {@code exact} / {@code prefix} / {@code suffix}
 */
public record UrlResource(Long id, String name, String path, String httpMethod, String matchType) {

    public static final String MATCH_EXACT = "exact";
    public static final String MATCH_PREFIX = "prefix";
    public static final String MATCH_SUFFIX = "suffix";

    /**
     * 判断本次请求是否命中本资源。
     *
     * <p>三个条件必须同时满足：方法匹配 + 路径按 matchType 匹配。
     * 任一为 null 视为不匹配——**默认拒绝**，宁可误拦也不要漏放。</p>
     *
     * @param requestPath   去掉 context-path 后的请求路径
     * @param requestMethod 请求方法，大写
     */
    public boolean matches(String requestPath, String requestMethod) {
        return matchesMethod(requestMethod) && matchesPath(requestPath);
    }

    boolean matchesMethod(String method) {
        if (httpMethod == null || httpMethod.isBlank()) {
            return false;
        }
        String declared = httpMethod.trim();
        if ("ALL".equalsIgnoreCase(declared) || "*".equals(declared)) {
            return true;
        }
        if (method == null) {
            return false;
        }
        for (String m : declared.split(",")) {
            if (m.trim().equalsIgnoreCase(method.trim())) {
                return true;
            }
        }
        return false;
    }

    boolean matchesPath(String requestPath) {
        if (path == null || path.isBlank() || requestPath == null) {
            return false;
        }
        String pattern = normalize(path);
        String actual = normalize(requestPath);
        if (MATCH_PREFIX.equalsIgnoreCase(matchType)) {
            return actual.equals(pattern) || actual.startsWith(pattern + "/");
        }
        if (MATCH_SUFFIX.equalsIgnoreCase(matchType)) {
            return actual.endsWith(pattern);
        }
        // 默认 exact：完全相等；另外容忍末尾斜杠差异
        return actual.equals(pattern) || actual.equals(pattern + "/");
    }

    /** 去掉末尾斜杠，保证 {@code /api/x/} 与 {@code /api/x} 等价 */
    private static String normalize(String p) {
        String s = p.trim();
        while (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
