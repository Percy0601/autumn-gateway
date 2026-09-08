package xyz.wewin.autumn.gateway.authorization.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;

import java.security.SecureRandom;

/**
 * SVG 验证码：零第三方依赖，纯字符串生成。
 *
 * <p>刻意不使用 java.awt / ImageIO 生成 PNG——AWT 在 GraalVM native image 下需要额外的
 * headless 与字体配置，容易踩坑。SVG 由文本直接拼出，native 编译零成本。</p>
 *
 * <p>验证码答案保存在 HTTP session（登录本身依赖 session，无需再引入缓存依赖）。</p>
 */
@org.springframework.stereotype.Service
public class CaptchaService {

    private static final String SESSION_KEY = "AUTUMN_LOGIN_CAPTCHA";

    /** 去掉了 0/O/1/l/I 等易混淆字符 */
    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int WIDTH = 120;
    private static final int HEIGHT = 44;

    private final SecureRandom random = new SecureRandom();

    @Value("${autumn.security.captcha.length:4}")
    private int length;

    @Value("${autumn.security.captcha.ttl-seconds:180}")
    private long ttlSeconds;

    /**
     * 生成验证码并写入 session，返回可直接作为图片输出的 SVG 文本
     */
    public String generate(HttpServletRequest request) {
        String code = randomCode();
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_KEY, new Captcha(code, System.currentTimeMillis() + ttlSeconds * 1000));
        return render(code);
    }

    /**
     * 校验验证码，一次性消费（无论成功失败都会失效，防止复用）
     */
    public boolean validate(HttpServletRequest request, String input) {
        HttpSession session = request.getSession(false);
        if (session == null || input == null || input.isBlank()) {
            return false;
        }
        Object attribute = session.getAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY);
        if (!(attribute instanceof Captcha captcha)) {
            return false;
        }
        if (System.currentTimeMillis() > captcha.expiresAtMillis()) {
            return false;
        }
        return captcha.code().equalsIgnoreCase(input.trim());
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(this.length);
        for (int i = 0; i < this.length; i++) {
            code.append(CHARS[this.random.nextInt(CHARS.length)]);
        }
        return code.toString();
    }

    /**
     * 渲染 SVG：随机颜色 + 随机旋转 + 干扰线 + 噪点
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

    /**
     * session 中保存的验证码
     */
    public record Captcha(String code, long expiresAtMillis) {
    }
}
