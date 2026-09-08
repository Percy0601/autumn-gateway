package xyz.wewin.autumn.gateway.authorization.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.authorization.security.CaptchaService;

/**
 * 登录验证码：直接返回 SVG，前端用 {@code <img src="/api/captcha">} 渲染，点击即刷新。
 */
@RestController
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping(path = "/api/captcha", produces = "image/svg+xml")
    public ResponseEntity<String> captcha(HttpServletRequest request) {
        String svg = this.captchaService.generate(request);
        return ResponseEntity.ok()
                .contentType(new MediaType("image", "svg+xml", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(svg);
    }
}
