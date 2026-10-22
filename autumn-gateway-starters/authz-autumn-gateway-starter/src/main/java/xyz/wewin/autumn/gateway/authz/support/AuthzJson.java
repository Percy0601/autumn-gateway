package xyz.wewin.autumn.gateway.authz.support;

import java.util.Collection;

/**
 * 极简 JSON 输出：只服务本模块的几个固定响应体（401/403/登录结果），
 * 因此刻意不引 Jackson——少一个依赖、native 下少一份反射元数据。
 *
 * <p>转义时顺手把 {@code < > &} 转成 unicode 转义，避免响应被浏览器当成 HTML 嗅探时的注入面。</p>
 */
public final class AuthzJson {

    private AuthzJson() {
    }

    /**
     * 按「key, value, key, value ...」拼对象。
     * value 支持 {@code null} / {@code Boolean} / {@code Number} / {@code Collection<String>} / 其它（按字符串）。
     */
    public static String object(Object... keyValues) {
        StringBuilder sb = new StringBuilder(128).append('{');
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escape(String.valueOf(keyValues[i]))).append("\":");
            appendValue(sb, keyValues[i + 1]);
        }
        return sb.append('}').toString();
    }

    public static String strings(Collection<String> values) {
        StringBuilder sb = new StringBuilder(64).append('[');
        boolean first = true;
        for (String value : values) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(escape(value == null ? "" : value)).append('"');
            first = false;
        }
        return sb.append(']').toString();
    }

    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
            return;
        }
        if (value instanceof Collection<?> items) {
            sb.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    sb.append(',');
                }
                sb.append('"').append(escape(item == null ? "" : item.toString())).append('"');
                first = false;
            }
            sb.append(']');
            return;
        }
        sb.append('"').append(escape(value.toString())).append('"');
    }

    static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
