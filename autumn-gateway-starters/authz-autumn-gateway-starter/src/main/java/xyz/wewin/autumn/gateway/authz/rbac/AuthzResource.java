package xyz.wewin.autumn.gateway.authz.rbac;

/**
 * 一条 URL 级资源（对应 {@code autumn.authz.sql.resource-query} 的固定别名）。
 *
 * @param path       resource_path：URL 模式，如 {@code /api/order/**}
 * @param httpMethod http_method：逗号分隔的多值，{@code ALL} 或 {@code *} 表示全部
 * @param matchType  match_type：{@code exact} / {@code prefix} / {@code suffix}
 */
public record AuthzResource(String path, String httpMethod, String matchType) {

    public static final String MATCH_EXACT = "exact";
    public static final String MATCH_PREFIX = "prefix";
    public static final String MATCH_SUFFIX = "suffix";
}
