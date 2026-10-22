package xyz.wewin.autumn.gateway.authz.support;

import org.springframework.util.AntPathMatcher;
import xyz.wewin.autumn.gateway.authz.rbac.AuthzResource;

/**
 * 资源匹配：Spring {@link AntPathMatcher} 判路径 + HTTP method 判方法。
 *
 * <h3>为什么用 AntPathMatcher 而不是正则</h3>
 * 运维在 {@code basic_resource.path} 里填的是 {@code /api/order/**} 这类人能读懂的模式，
 * Ant 风格是 Spring 生态的通用写法，和网关自身的 Path 断言、Spring Security 的
 * {@code pathMatchers} 语义一致，不会出现「同一个表达式两处解释不同」的坑。
 *
 * <h3>默认拒绝</h3>
 * 任一字段为空即视为不匹配。宁可误拦（会立刻被发现并补授权），也不要漏放。
 */
public final class AuthzResourceMatcher {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final String METHOD_ALL = "ALL";
    private static final String METHOD_ANY = "*";

    private AuthzResourceMatcher() {
    }

    public static boolean matches(AuthzResource resource, String requestPath, String requestMethod) {
        if (resource == null) {
            return false;
        }
        return matchesMethod(resource.httpMethod(), requestMethod)
                && matchesPath(resource.path(), resource.matchType(), requestPath);
    }

    static boolean matchesMethod(String declared, String requestMethod) {
        if (declared == null || declared.isBlank() || requestMethod == null) {
            return false;
        }
        String trimmed = declared.trim();
        if (METHOD_ALL.equalsIgnoreCase(trimmed) || METHOD_ANY.equals(trimmed)) {
            return true;
        }
        for (String part : trimmed.split(",")) {
            if (part.trim().equalsIgnoreCase(requestMethod.trim())) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesPath(String pattern, String matchType, String requestPath) {
        if (pattern == null || pattern.isBlank() || requestPath == null) {
            return false;
        }
        String actual = normalize(requestPath);
        String expected = normalize(pattern);

        if (AuthzResource.MATCH_PREFIX.equalsIgnoreCase(matchType)) {
            return actual.equals(expected) || actual.startsWith(expected + "/");
        }
        if (AuthzResource.MATCH_SUFFIX.equalsIgnoreCase(matchType)) {
            return actual.endsWith(expected);
        }
        // 默认 exact：先按 Ant 模式比（含 ** 通配），再容忍末尾斜杠差异
        return PATH_MATCHER.match(expected, actual) || PATH_MATCHER.match(expected + "/", actual);
    }

    /** 去掉末尾斜杠，保证 {@code /api/x/} 与 {@code /api/x} 等价 */
    private static String normalize(String path) {
        String value = path.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
