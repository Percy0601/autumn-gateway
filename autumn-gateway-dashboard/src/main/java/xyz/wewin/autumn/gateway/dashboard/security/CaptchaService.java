package xyz.wewin.autumn.gateway.dashboard.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录验证码：SVG 零第三方依赖，纯字符串生成。
 *
 * <p>与授权服务器（{@code autumn-gateway-authorization} 的 CaptchaService）保持同一套 SVG 画法，
 * 差异只在答案的存储方式：</p>
 * <ul>
 *   <li>授权服务器登录本身依赖 HTTP session，答案存 session；</li>
 *   <li>本服务是<b>完全无状态</b>的 JWT 架构（{@code SessionCreationPolicy.STATELESS}，不建 Session、
 *       水平扩容无需粘性会话），因此答案存内存 {@link ConcurrentHashMap}，以 captchaId 关联：
 *       生成时通过 HttpOnly Cookie 下发 captchaId（前端无需感知），登录时服务端从 Cookie 读取校验。</li>
 * </ul>
 *
 * <p>安全性约定：</p>
 * <ul>
 *   <li>一次性消费：无论校验成功失败都会移除，防重放；</li>
 *   <li>带 TTL（默认 180s），过期失效；</li>
 *   <li>生成时顺手清理过期项，容量超上限时整体重置（验证码是一次性低价值数据，可承受）；</li>
 *   <li>单节点部署适用；多节点水平扩容需换 Redis 等共享存储（见 {@code autumn.captcha.*} 配置注释）。</li>
 * </ul>
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    /** 去掉了 0/O/1/l/I 等易混淆字符 */
    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int WIDTH = 120;
    private static final int HEIGHT = 44;

    /** 内存容量上限：超出先清过期项，仍超限则整体重置（防内存泄漏的兜底） */
    private static final int MAX_CACHE_SIZE = 10_000;

    /** 验证码 Cookie 名：/api/captcha 下发，登录时服务端从 Cookie 读取 captchaId 关联答案 */
    public static final String CAPTCHA_COOKIE = "captchaId";

    private final SecureRandom random = new SecureRandom();

    /** captchaId -> 验证码答案（含过期时间） */
    private final Map<String, Captcha> cache = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final int length;
    private final long ttlMillis;

    public CaptchaService(@Value("${autumn.captcha.enabled:true}") boolean enabled,
                          @Value("${autumn.captcha.length:4}") int length,
                          @Value("${autumn.captcha.ttl-seconds:180}") long ttlSeconds) {
        this.enabled = enabled;
        this.length = length;
        this.ttlMillis = ttlSeconds * 1000;
        log.info("登录验证码开关 autumn.captcha.enabled = {}（length={}, ttl={}s）", enabled, length, ttlSeconds);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /** 验证码有效期（秒）：Cookie Max-Age 与内存 TTL 保持一致 */
    public long getTtlSeconds() {
        return this.ttlMillis / 1000;
    }

    /**
     * 生成验证码：返回 captchaId（由 {@code /api/captcha} 以 HttpOnly Cookie 下发，登录时服务端据此取答案）
     * + 可直接作为 {@code <img src>} 输出的 SVG 源码（与授权服务器同一套画法）。
     */
    public CaptchaImage generate() {
        purgeExpired();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode();
        this.cache.put(captchaId, new Captcha(code, System.currentTimeMillis() + this.ttlMillis));
        return new CaptchaImage(captchaId, render(code));
    }

    /**
     * 校验验证码，一次性消费（无论成功失败都会移除，防止复用）。
     */
    public boolean validate(String captchaId, String input) {
        if (captchaId == null || captchaId.isBlank() || input == null || input.isBlank()) {
            return false;
        }
        Captcha captcha = this.cache.remove(captchaId);
        if (captcha == null) {
            return false;
        }
        if (System.currentTimeMillis() > captcha.expiresAtMillis()) {
            return false;
        }
        return captcha.code().equalsIgnoreCase(input.trim());
    }

    /** 清理过期项；容量超上限时整体重置（验证码一次性低价值，可承受） */
    private void purgeExpired() {
        long now = System.currentTimeMillis();
        this.cache.values().removeIf(c -> now > c.expiresAtMillis());
        if (this.cache.size() > MAX_CACHE_SIZE) {
            log.warn("验证码缓存超过上限 {}，整体重置（当前 size={}）", MAX_CACHE_SIZE, this.cache.size());
            this.cache.clear();
        }
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(this.length);
        for (int i = 0; i < this.length; i++) {
            code.append(CHARS[this.random.nextInt(CHARS.length)]);
        }
        return code.toString();
    }

    /**
     * 渲染 SVG：随机颜色 + 随机旋转 + 干扰线 + 噪点。
     *
     * <p>刻意不使用 java.awt / ImageIO 生成 PNG——headless 环境与字体配置容易踩坑，
     * SVG 由文本直接拼出，零依赖且无环境依赖。</p>
     */
    private String render(String code) {
        StringBuilder svg = new StringBuilder(1024);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(WIDTH)
                .append("\" height=\"").append(HEIGHT)
                .append("\" viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT).append("\">");

        // 背景
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"")
                .append(randomColor(230, 250)).append("\"/>");

        // 干扰线
        for (int i = 0; i < 4; i++) {
            svg.append("<line x1=\"").append(this.random.nextInt(WIDTH))
                    .append("\" y1=\"").append(this.random.nextInt(HEIGHT))
                    .append("\" x2=\"").append(this.random.nextInt(WIDTH))
                    .append("\" y2=\"").append(this.random.nextInt(HEIGHT))
                    .append("\" stroke=\"").append(randomColor(120, 200))
                    .append("\" stroke-width=\"1\" opacity=\"0.7\"/>");
        }

        // 噪点
        for (int i = 0; i < 24; i++) {
            svg.append("<circle cx=\"").append(this.random.nextInt(WIDTH))
                    .append("\" cy=\"").append(this.random.nextInt(HEIGHT))
                    .append("\" r=\"1\" fill=\"").append(randomColor(100, 220))
                    .append("\" opacity=\"0.6\"/>");
        }

        // 字符
        int perChar = (WIDTH - 12) / Math.max(1, code.length());
        for (int i = 0; i < code.length(); i++) {
            int x = 8 + i * perChar;
            int y = HEIGHT / 2 + 6 + this.random.nextInt(6) - 3;
            int rotate = this.random.nextInt(30) - 15;
            svg.append("<text x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" font-family=\"Verdana,DejaVu Sans,Helvetica,Arial,sans-serif\"")
                    .append(" font-size=\"").append(22 + this.random.nextInt(6))
                    .append("\" font-weight=\"bold\" fill=\"").append(randomColor(20, 120))
                    .append("\" transform=\"rotate(").append(rotate).append(' ')
                    .append(x).append(' ').append(y).append(")\">")
                    .append(code.charAt(i))
                    .append("</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private String randomColor(int min, int max) {
        int r = min + this.random.nextInt(max - min + 1);
        int g = min + this.random.nextInt(max - min + 1);
        int b = min + this.random.nextInt(max - min + 1);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    /** 验证码答案（含过期时间戳） */
    public record Captcha(String code, long expiresAtMillis) {
    }

    /** 生成结果：captchaId 供服务端关联答案，svg 为可直接输出的 SVG 图片源码 */
    public record CaptchaImage(String captchaId, String svg) {
    }
}
