package xyz.wewin.autumn.gateway.dashboard.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.security.CaptchaService;

import java.nio.charset.StandardCharsets;

/**
 * 登录验证码：直出 SVG 图片（与授权服务器同一套画法），前端 {@code <img src="/api/captcha">} 渲染，点击即刷新。
 *
 * <p>与授权服务器的差异：授权服务器把答案存 HTTP session，直接吐 SVG 图片；
 * 本服务无状态（JWT，{@code SessionCreationPolicy.STATELESS}，不建 Session、水平扩容无需粘性会话），
 * 因此答案按 captchaId 存内存（见 {@link CaptchaService}），captchaId 通过 <b>HttpOnly Cookie</b> 下发：
 * 前端无需感知 captchaId，登录时服务端从 Cookie 读取并关联答案校验——仍是无状态方案。</p>
 *
 * <p>注意：{@code autumn.captcha.enabled=false} 时该接口仍返回验证码图片，但登录不再校验——
 * 便于灰度关闭验证码时前端无需改动。</p>
 *
 * <p><b>响应头约定</b>：{@code Cache-Control: no-store} 必须保留。Spring 的 404/500 错误响应
 * 默认不带缓存指令，浏览器会按启发式规则缓存——旧部署（无此接口）时用户浏览器里缓存的
 * 404 会让「后端已修好但前端仍拿旧错误」持续复现，表现为验证码位置静默失败。
 * 一次性 token 接口本就不允许任何中间层缓存。</p>
 */
@RestController
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping(path = "/api/captcha", produces = "image/svg+xml")
    public ResponseEntity<String> captcha(HttpServletResponse response) {
        CaptchaService.CaptchaImage image = this.captchaService.generate();
        Cookie cookie = new Cookie(CaptchaService.CAPTCHA_COOKIE, image.captchaId());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge((int) this.captchaService.getTtlSeconds());
        // Servlet 6.0+ 支持任意属性：SameSite=Lax 与本站 first-party 请求行为一致
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return ResponseEntity.ok()
                .contentType(new MediaType("image", "svg+xml", StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(image.svg());
    }
}
