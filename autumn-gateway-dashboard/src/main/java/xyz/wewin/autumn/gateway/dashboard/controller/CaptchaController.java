package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.security.CaptchaService;

import java.time.Duration;
import java.util.Map;

/**
 * 登录验证码：返回 captchaId + SVG dataURI，前端 {@code <img :src>} 渲染，点击图片即刷新。
 *
 * <p>与授权服务器的差异：授权服务器验证码答案存 HTTP session，直接吐 SVG 图片；
 * 本服务无状态（JWT），答案按 captchaId 存内存，因此以 JSON 返回 dataURI + captchaId。</p>
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

    @GetMapping("/api/captcha")
    public ResponseEntity<Map<String, Object>> captcha() {
        CaptchaService.CaptchaImage image = this.captchaService.generate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(Map.of("captchaId", image.captchaId(), "img", image.dataUri()));
    }
}
