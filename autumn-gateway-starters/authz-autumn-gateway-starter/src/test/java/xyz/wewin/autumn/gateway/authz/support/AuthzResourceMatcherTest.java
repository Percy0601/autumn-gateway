package xyz.wewin.autumn.gateway.authz.support;

import org.junit.jupiter.api.Test;
import xyz.wewin.autumn.gateway.authz.rbac.AuthzResource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 资源匹配测试：AntPathMatcher 判路径 + HTTP method 判方法，默认拒绝。 */
class AuthzResourceMatcherTest {

    @Test
    void exactMatchUsesAntPattern() {
        AuthzResource resource = new AuthzResource("/api/order/**", "GET", "exact");

        assertTrue(AuthzResourceMatcher.matches(resource, "/api/order/1001", "GET"));
        assertTrue(AuthzResourceMatcher.matches(resource, "/api/order/1001/items", "GET"));
        assertFalse(AuthzResourceMatcher.matches(resource, "/api/user/1", "GET"));
    }

    @Test
    void exactMatchToleratesTrailingSlash() {
        AuthzResource resource = new AuthzResource("/api/order", "POST", "exact");

        assertTrue(AuthzResourceMatcher.matches(resource, "/api/order/", "POST"));
    }

    @Test
    void prefixMatchDoesNotSwallowSiblingPath() {
        AuthzResource resource = new AuthzResource("/api/order", "GET", "prefix");

        assertTrue(AuthzResourceMatcher.matches(resource, "/api/order", "GET"));
        assertTrue(AuthzResourceMatcher.matches(resource, "/api/order/1001", "GET"));
        // 前缀匹配必须按路径段收敛，否则 /api/order-admin 会被误放行
        assertFalse(AuthzResourceMatcher.matches(resource, "/api/order-admin/1", "GET"));
    }

    @Test
    void suffixMatch() {
        AuthzResource resource = new AuthzResource("/export", "GET", "suffix");

        assertTrue(AuthzResourceMatcher.matches(resource, "/api/order/export", "GET"));
        assertFalse(AuthzResourceMatcher.matches(resource, "/api/order/export/detail", "GET"));
    }

    @Test
    void methodMatchSupportsAllAndMultiValue() {
        assertTrue(AuthzResourceMatcher.matches(new AuthzResource("/api/**", "ALL", "exact"), "/api/x", "DELETE"));
        assertTrue(AuthzResourceMatcher.matches(new AuthzResource("/api/**", "*", "exact"), "/api/x", "DELETE"));
        assertTrue(AuthzResourceMatcher.matches(new AuthzResource("/api/**", "GET,POST", "exact"), "/api/x", "post"));
        assertFalse(AuthzResourceMatcher.matches(new AuthzResource("/api/**", "GET,POST", "exact"), "/api/x", "PUT"));
    }

    /** 任一字段为空即不匹配：宁可误拦也不要漏放 */
    @Test
    void blankFieldsNeverMatch() {
        assertFalse(AuthzResourceMatcher.matches(new AuthzResource(null, "GET", "exact"), "/api/x", "GET"));
        assertFalse(AuthzResourceMatcher.matches(new AuthzResource("/api/x", null, "exact"), "/api/x", "GET"));
        assertFalse(AuthzResourceMatcher.matches(new AuthzResource("/api/x", "GET", "exact"), null, "GET"));
        assertFalse(AuthzResourceMatcher.matches(null, "/api/x", "GET"));
    }
}
