package xyz.wewin.autumn.gateway.dashboard.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.dto.LoginRequest;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRepository;
import xyz.wewin.autumn.gateway.dashboard.security.CaptchaService;
import xyz.wewin.autumn.gateway.dashboard.security.JwtUtil;
import xyz.wewin.autumn.gateway.dashboard.security.LoginUser;
import xyz.wewin.autumn.gateway.dashboard.security.Permissions;
import xyz.wewin.autumn.gateway.dashboard.service.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ant-design-pro 官方模板登录协议实现（对应前端 services/ant-design-pro/api.ts）：
 *
 * <pre>
 *   POST /api/login/account   登录：{username, password, autoLogin, type}
 *   GET  /api/currentUser     当前用户信息（需携带 Authorization: Bearer &lt;token&gt;）
 *   POST /api/login/outLogin  退出登录
 * </pre>
 *
 * 与 {@link AuthController} 的关系：AuthController 是自定义的 /auth/** 协议（内部/网关使用），
 * 本 Controller 是对外提供 ant-design-pro 模板兼容协议（前端页面直连），两者共用
 * basic_user + BCrypt + JWT 这套账号体系。
 */
@RestController
public class LoginController {

    @Autowired
    private BasicUserRepository basicUserRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CaptchaService captchaService;

    /**
     * 登录：校验用户名密码（basic_user），成功返回 JWT token。
     *
     * <p>响应遵循 ant-design-pro 官方格式：
     * 成功 {@code {status:'ok', type, currentAuthority, token, userid, name}}；
     * 失败 {@code {status:'error', type, currentAuthority:'guest', message}}（不抛异常，前端展示错误提示）。
     * {@code message} 为服务端给出的具体原因（验证码错误 / 账号或密码错误 / 账号已禁用），
     * 前端优先展示，缺失时回落前端默认文案。</p>
     *
     * <p>防暴力破解：验证码在账号密码校验<b>之前</b>拦截，不通过直接返回，不走认证流程；
     * 密码错误累计达到上限会禁用账号（见 {@link UserService}）。</p>
     */
    @PostMapping("/api/login/account")
    public Map<String, Object> account(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String type = request.getType() == null ? "account" : request.getType();
        // 1) 验证码校验（一次性消费）：不通过直接返回，从源头挡住暴力提交。
        // captchaId 优先取 Cookie（直出图片方案由 /api/captcha 下发），请求体字段仅作兼容旧流程。
        String captchaId = resolveCaptchaId(httpRequest, request.getCaptchaId());
        if (this.captchaService.isEnabled()
                && !this.captchaService.validate(captchaId, request.getCaptcha())) {
            return loginError(type, "验证码错误或已失效，请重新输入");
        }
        try {
            // 2) 统一认证入口：按登录名查找 basic_user 并校验 BCrypt 密码与账号状态
            BasicUser user = userService.authenticate(UserService.IDENTITY_TYPE_PASSWORD,
                    request.getUsername(), request.getPassword());

            String token = jwtUtil.generateToken(user.getId(), user.getUsername());
            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("type", type);
            result.put("currentAuthority", resolveAccess(user));
            result.put("token", token);
            result.put("userid", String.valueOf(user.getId()));
            result.put("name", user.getNickname() != null && !user.getNickname().isBlank()
                    ? user.getNickname() : user.getUsername());
            return result;
        } catch (IllegalArgumentException e) {
            // 3) 认证失败：把服务端判定原因带回前端（账号或密码错误 / 账号已禁用）
            return loginError(type, e.getMessage());
        }
    }

    /** 登录失败响应：ant-design-pro 协议 + message 具体原因 */
    private Map<String, Object> loginError(String type, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "error");
        result.put("type", type);
        result.put("currentAuthority", "guest");
        result.put("message", message);
        return result;
    }

    /** captchaId 优先取 Cookie（直出图片方案），请求体字段仅作旧前端 JSON 流程的兼容 */
    private String resolveCaptchaId(HttpServletRequest httpRequest, String bodyCaptchaId) {
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (CaptchaService.CAPTCHA_COOKIE.equals(cookie.getName())
                        && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return bodyCaptchaId;
    }

    /**
     * 当前登录用户信息：从 Authorization: Bearer &lt;token&gt; 解析用户。
     *
     * <p>未登录/无效 token 返回 401（前端 request 拦截器会 reject，
     * getInitialState 捕获后自动跳转登录页）。</p>
     *
     * <p>v6 起额外下发两个权限相关字段：
     * <ul>
     *   <li>{@code access}：保留原有 admin/user 语义，前端 access.ts 仍在用；</li>
     *   <li>{@code authorityList}：按钮级权限标识清单，前端 v-auth 指令据此显隐。
     *       超管下发 {@code ["*"]}，前端按通配处理。</li>
     * </ul>
     */
    @GetMapping("/api/currentUser")
    public ResponseEntity<Map<String, Object>> currentUser(HttpServletRequest request,
                                                           @AuthenticationPrincipal LoginUser loginUser) {
        // 优先用过滤器装好的 principal（含权限），避免二次查库与二次验签
        Long userId = loginUser != null ? loginUser.getUserId() : resolveUserId(request);
        BasicUser user = userId == null ? null : basicUserRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "data", Map.of("access", "guest")));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("name", user.getNickname() != null && !user.getNickname().isBlank()
                ? user.getNickname() : user.getUsername());
        data.put("avatar", user.getAvatar());
        data.put("userid", String.valueOf(user.getId()));
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("access", resolveAccess(user));
        // 按钮级权限清单：前端 v-auth="'system:user:create'" 依赖此字段
        List<String> authorities = loginUser != null
                ? Permissions.currentAuthorities()
                : List.of();
        data.put("authorityList", authorities);
        data.put("superAdmin", loginUser != null && loginUser.isSuperAdmin());
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    /**
     * 退出登录：前端已移除本地 token，这里按官方协议返回 success 即可。
     */
    @PostMapping("/api/login/outLogin")
    public Map<String, Object> outLogin() {
        return Map.of("success", true, "data", Map.of());
    }

    private Long resolveUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    /**
     * 权限判定：basic_user.admin = 1 → admin，否则 user。
     * 前端 access.ts 依赖 currentUser.access === 'admin' 决定管理权限。
     */
    private String resolveAccess(BasicUser user) {
        return user.getAdmin() != null && user.getAdmin() == 1 ? "admin" : "user";
    }
}
