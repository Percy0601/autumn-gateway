package xyz.wewin.autumn.gateway.authorization.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 极简 JSON 响应工具。
 *
 * <p>登录相关接口的响应字段都是可控的字符串/数字/布尔，
 * 这里手写序列化，避免为了几个字段引入 Jackson 依赖（也让 native image 更干净）。</p>
 */
public final class JsonResponses {

    private JsonResponses() {
    }

    public static void write(HttpServletResponse response, int status, Map<String, Object> body)
            throws IOException {
        StringBuilder json = new StringBuilder(256).append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':');
            appendValue(json, entry.getValue());
        }
        json.append('}');

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(json.toString());
    }

    private static void appendValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else {
            json.append(quote(value.toString()));
        }
    }

    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }
}
