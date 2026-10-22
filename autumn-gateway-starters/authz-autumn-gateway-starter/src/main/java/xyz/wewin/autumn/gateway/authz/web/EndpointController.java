package xyz.wewin.autumn.gateway.authz.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.wewin.autumn.gateway.authz.autoconfigure.AuthzGatewayProperties;
import xyz.wewin.autumn.gateway.authz.captcha.AuthzCaptchaService;
import xyz.wewin.autumn.gateway.authz.support.AuthzJson;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 鉴权 starter 自带的本地端点：{@code /gateway/endpoint/captcha} 与 {@code /gateway/endpoint/login}。
 *
 * <p>放在网关本地（而不是转发给下游）是有意的：登录是凭据入口，必须在离客户端最近的这一跳终结，
 * 令牌也由网关自己签发，下游服务只负责验签。</p>
 *
 * <h3>协议：兼容旧 dashboard 的 ant-design-pro 登录契约</h3>
 * <pre>
 *   成功  {"status":"ok","type":"account","currentAuthority":["admin"],
 *          "token":"&lt;jwt&gt;","userid":"1","name":"admin"}
 *   失败  {"status":"error","type":"account","currentAuthority":"guest","message":"..."}
 * </pre>
 * 失败也返回 HTTP 200——旧前端只看 {@code status} 字段，返回 401 会让它走全局错误处理、
 * 拿不到 {@code message}，表现为「点了登录没反应」。
 *
 * <h3>验证码</h3>
 * {@code GET} 直出 SVG，答案按 captchaId 存内存，captchaId 通过 <b>HttpOnly Cookie</b> 下发：
 * 前端只管把用户输入的字符串回传，不必感知 captchaId。
 * 响应带 {@code Cache-Control: no-store}——一次性 token 接口不允许任何中间层缓存。
 */
@RestController
@RequestMapping("/gateway")
public class EndpointController {

    private static final MediaType SVG = new MediaType("image", "svg+xml", StandardCharsets.UTF_8);

    private final AuthzCaptchaService captchaService;
    private final AuthzLoginService loginService;
    private final AuthzGatewayProperties properties;

    public EndpointController(AuthzCaptchaService captchaService,
                              AuthzLoginService loginService,
                              AuthzGatewayProperties properties) {
        this.captchaService = captchaService;
        this.loginService = loginService;
        this.properties = properties;
    }

    /** 登录验证码：直出 SVG 图片，前端 {@code <img src="/gateway/endpoint/captcha">} 渲染，点击即刷新。 */
    @GetMapping(path = "/endpoint/captcha", produces = "image/svg+xml")
    public Mono<ResponseEntity<String>> captcha(ServerWebExchange exchange) {
        AuthzCaptchaService.CaptchaImage image = this.captchaService.generate();
        long ttlSeconds = this.captchaService.getTtlSeconds();
        exchange.getResponse().addCookie(ResponseCookie
                .from(this.properties.getCaptcha().getCookieName(), image.captchaId())
                .path("/")
                .httpOnly(true)
                .sameSite("Lax")
                .maxAge(Duration.ofSeconds(ttlSeconds))
                .build());
        return Mono.just(ResponseEntity.ok()
                .contentType(SVG)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(image.svg()));
    }

    /**
     * 账号密码登录。
     *
     * @param body {@code username / password / captcha / type / autoLogin}
     */
    @PostMapping(path = "/endpoint/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> login(@RequestBody Map<String, Object> body,
                                              ServerWebExchange exchange) {
        String type = text(body, "type", "account");
        String username = text(body, "username", "");
        String password = text(body, "password", "");
        String captcha = text(body, "captcha", "");

        if (this.loginService == null) {
            return Mono.just(json(HttpStatus.SERVICE_UNAVAILABLE,
                    failure(type, "登录服务未启用：缺少 DataSource 配置")));
        }
        String captchaError = checkCaptcha(exchange, captcha);
        if (captchaError != null) {
            return Mono.just(json(HttpStatus.OK, failure(type, captchaError)));
        }

        return this.loginService.login(username, password)
                .map(result -> result.ok()
                        ? json(HttpStatus.OK, success(type, result))
                        : json(HttpStatus.OK, failure(type, result.message())));
    }

    /** 验证码校验：关闭开关时直接通过，便于灰度关闭而前端无需改动。 */
    private String checkCaptcha(ServerWebExchange exchange, String input) {
        if (!this.captchaService.isEnabled()) {
            return null;
        }
        if (input == null || input.isBlank()) {
            return "请输入验证码";
        }
        ServerHttpRequest request = exchange.getRequest();
        var cookie = request.getCookies().getFirst(this.properties.getCaptcha().getCookieName());
        if (cookie == null || !this.captchaService.validate(cookie.getValue(), input)) {
            return "验证码错误或已过期";
        }
        return null;
    }

    private static String success(String type, AuthzLoginService.LoginResult result) {
        List<String> authority = result.authorities().isEmpty()
                ? List.of(result.admin() ? "admin" : "user")
                : result.authorities();
        return AuthzJson.object(
                "status", "ok",
                "type", type,
                "currentAuthority", authority,
                "token", result.token(),
                "userid", result.userId(),
                "name", result.username());
    }

    private static String failure(String type, String message) {
        return AuthzJson.object(
                "status", "error",
                "type", type,
                "currentAuthority", "guest",
                "message", message);
    }

    private static ResponseEntity<String> json(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private static String text(Map<String, Object> body, String key, String fallback) {
        Object value = (body == null) ? null : body.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
