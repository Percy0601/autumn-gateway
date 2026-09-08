package xyz.wewin.autumn.gateway.authorization.security;

import org.springframework.stereotype.Service;
import xyz.wewin.autumn.gateway.authorization.config.WeChatProperties;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 微信开放平台网站应用登录：封装"跳授权页 → 换 token → 取用户资料"三步。
 *
 * <p>刻意零第三方依赖、不用 Jackson：HTTP 用 JDK 内置 {@link HttpClient}，
 * 响应用自写的轻量 JSON 提取（微信返回的是扁平 JSON，无需完整解析器）。
 * 这既贴合本模块"依赖最小化、GraalVM native image 友好"的取向，也避免引入反射型 JSON 库。</p>
 */
@Service
public class WeChatService {

    private final WeChatProperties props;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public WeChatService(WeChatProperties props) {
        this.props = props;
    }

    /**
     * 构造微信授权页地址。state 由调用方生成并存入 session，回调时校验以防 CSRF。
     */
    public String buildAuthorizeUrl(String state) {
        return props.getAuthorizeUrl()
                + "?appid=" + props.getAppId()
                + "&redirect_uri=" + URLEncoder.encode(props.getRedirectUri(), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode(props.getScope(), StandardCharsets.UTF_8)
                + "&state=" + state
                + "#wechat_redirect";
    }

    /**
     * 用授权码换取用户资料；任意环节失败抛出 RuntimeException，由回调控制器转成登录页错误提示。
     */
    public WeChatUserInfo exchange(String code) {
        String tokenJson = get(props.getTokenUrl()
                + "?appid=" + props.getAppId()
                + "&secret=" + props.getAppSecret()
                + "&code=" + code
                + "&grant_type=authorization_code");
        requireNoError(tokenJson, "换取 access_token");
        String accessToken = requireString(tokenJson, "access_token", "换取 access_token");
        String openid = requireString(tokenJson, "openid", "换取 openid");
        String unionid = extractString(tokenJson, "unionid");

        String userJson = get(props.getUserInfoUrl()
                + "?access_token=" + accessToken
                + "&openid=" + openid
                + "&lang=zh_CN");
        requireNoError(userJson, "获取用户资料");
        String nickname = extractString(userJson, "nickname");
        String headimgurl = extractString(userJson, "headimgurl");
        return new WeChatUserInfo(openid, unionid, nickname == null ? "" : nickname, headimgurl == null ? "" : headimgurl);
    }

    /**
     * 生成本地账号映射用的主标识：优先 unionid（跨应用打通），缺失时回退 openid。
     */
    public static String principalId(WeChatUserInfo info) {
        return (info.unionid() != null && !info.unionid().isBlank()) ? info.unionid() : info.openid();
    }

    /**
     * 生成随机 state（透传给微信，回调时校验）。
     */
    public static String randomState() {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("微信接口返回 HTTP " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("调用微信接口失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取字符串字段（处理 \" 与常见转义）。返回 null 表示字段不存在。
     */
    private static String extractString(String json, String key) {
        String marker = "\"" + key + "\"";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + marker.length());
        if (colon < 0) {
            return null;
        }
        int p = colon + 1;
        int len = json.length();
        while (p < len && Character.isWhitespace(json.charAt(p))) {
            p++;
        }
        if (p >= len || json.charAt(p) != '"') {
            return null;
        }
        p++;
        StringBuilder sb = new StringBuilder();
        while (p < len) {
            char c = json.charAt(p);
            if (c == '\\') {
                if (p + 1 >= len) {
                    break;
                }
                char n = json.charAt(p + 1);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'u' -> {
                        if (p + 5 < len) {
                            sb.append(json, p, p + 6);
                            p += 5;
                        }
                    }
                    default -> sb.append(n);
                }
                p += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                p++;
            }
        }
        return sb.toString();
    }

    /**
     * 提取整型字段（如 errcode）。返回 null 表示字段不存在。
     */
    private static Integer extractInt(String json, String key) {
        String marker = "\"" + key + "\"";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + marker.length());
        if (colon < 0) {
            return null;
        }
        int p = colon + 1;
        int len = json.length();
        while (p < len && Character.isWhitespace(json.charAt(p))) {
            p++;
        }
        int start = p;
        while (p < len && (Character.isDigit(json.charAt(p)) || json.charAt(p) == '-')) {
            p++;
        }
        if (p == start) {
            return null;
        }
        return Integer.parseInt(json.substring(start, p));
    }

    private static void requireNoError(String json, String stage) {
        Integer errcode = extractInt(json, "errcode");
        if (errcode != null && errcode != 0) {
            String errmsg = extractString(json, "errmsg");
            throw new IllegalStateException(stage + " 失败: errcode=" + errcode
                    + ", errmsg=" + (errmsg == null ? "" : errmsg));
        }
    }

    private static String requireString(String json, String key, String stage) {
        String value = extractString(json, key);
        if (value == null) {
            throw new IllegalStateException(stage + " 响应缺少 " + key);
        }
        return value;
    }

    /**
     * 微信返回的用户资料。
     */
    public record WeChatUserInfo(String openid, String unionid, String nickname, String headimgurl) {
    }
}
