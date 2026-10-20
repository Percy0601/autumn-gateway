package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.security.CaptchaService;

import java.util.Map;

/**
 * 登录验证码：返回 captchaId + SVG dataURI，前端 {@code <img :src>} 渲染，点击图片即刷新。
 *
 * <p>与授权服务器的差异：授权服务器验证码答案存 HTTP session，直接吐 SVG 图片；
 * 本服务无状态（JWT），答案按 captchaId 存内存，因此以 JSON 返回 dataURI + captchaId。</p>
 *
 * <p>注意：{@code autumn.captcha.enabled=false} 时该接口仍返回验证码图片，但登录不再校验——
 * 便于灰度关闭验证码时前端无需改动。</p>
 */
@RestController
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping("/api/captcha")
    public Map<String, Object> captcha() {
        CaptchaService.CaptchaImage image = this.captchaService.generate();
        return Map.of("captchaId", image.captchaId(), "img", image.dataUri());
    }
}
