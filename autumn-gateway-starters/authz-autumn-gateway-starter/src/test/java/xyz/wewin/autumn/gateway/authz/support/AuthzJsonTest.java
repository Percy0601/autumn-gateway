package xyz.wewin.autumn.gateway.authz.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 401/403 与登录响应体的 JSON 拼装：手写转义，不引 Jackson。 */
class AuthzJsonTest {

    @Test
    void objectHandlesScalarAndCollectionValues() {
        assertEquals("{\"status\":401,\"error\":\"unauthorized\",\"ok\":true,\"name\":null}",
                AuthzJson.object("status", 401, "error", "unauthorized", "ok", true, "name", null));
        assertEquals("{\"currentAuthority\":[\"admin\",\"ops\"]}",
                AuthzJson.object("currentAuthority", List.of("admin", "ops")));
    }

    /** 用户名等外部输入进响应体时必须转义，否则引号/反斜杠会破坏 JSON 结构 */
    @Test
    void escapesQuotesAndBackslashes() {
        assertEquals("{\"message\":\"say \\\"hi\\\" \\\\ ok\"}",
                AuthzJson.object("message", "say \"hi\" \\ ok"));
    }

    /** < > & 转 unicode，避免响应被浏览器嗅探成 HTML 时的注入面 */
    @Test
    void escapesHtmlSensitiveCharacters() {
        assertEquals("{\"message\":\"\\u003cscript\\u003e\\u0026\"}",
                AuthzJson.object("message", "<script>&"));
    }
}
