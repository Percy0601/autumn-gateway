package xyz.wewin.autumn.gateway.authorization.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * 登录页与 CSRF 接口。
 *
 * <p>登录页是前端构建产物（static/index.html），这里只做转发，
 * 不引入 Thymeleaf / Freemarker 等模板引擎——保持后端依赖最小，
 * 也让 GraalVM native image 构建更干净。</p>
 */
@Controller
public class LoginPageController {

    /**
     * 登录页（Spring Security formLogin 配置的 loginPage 指向这里）
     */
    @GetMapping("/login")
    public String loginPage() {
        return "forward:/index.html";
    }

    /**
     * 根路径同样交给前端
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    /**
     * 前端拿 CSRF 令牌：登录表单需要带 _csrf
     */
    @GetMapping("/api/csrf")
    @ResponseBody
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return Map.of();
        }
        return Map.of(
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getToken());
    }
}
