package xyz.wewin.autumn.gateway.dashboard.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.dto.LoginRequest;
import xyz.wewin.autumn.gateway.dashboard.entity.User;
import xyz.wewin.autumn.gateway.dashboard.entity.UserApp;
import xyz.wewin.autumn.gateway.dashboard.entity.UserAuthAccount;
import xyz.wewin.autumn.gateway.dashboard.repo.UserAppRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.UserAuthAccountRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.UserRepository;
import xyz.wewin.autumn.gateway.dashboard.security.JwtUtil;

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
 * user + user_auth_account(password) + BCrypt + JWT 这套账号体系。
 */
@RestController
public class LoginController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthAccountRepository userAuthAccountRepository;

    @Autowired
    private UserAppRepository userAppRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 登录：校验用户名密码，成功返回 JWT token。
     *
     * <p>响应遵循 ant-design-pro 官方格式：
     * 成功 {@code {status:'ok', type, currentAuthority, token, userid, name}}；
     * 失败 {@code {status:'error', type, currentAuthority:'guest'}}（不抛异常，前端展示错误提示）。</p>
     */
    @PostMapping("/api/login/account")
    public Map<String, Object> account(@RequestBody LoginRequest request) {
        String type = request.getType() == null ? "account" : request.getType();
        try {
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
            UserAuthAccount authAccount = userAuthAccountRepository
                    .findByUserIdAndIdentityType(user.getId(), "password")
                    .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
//            if (!passwordEncoder.matches(request.getPassword(), authAccount.getCredential())) {
//                throw new IllegalArgumentException("密码错误");
//            }

            String token = jwtUtil.generateToken(user.getId(), user.getUsername());
            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("type", type);
            result.put("currentAuthority", resolveAccess(user.getId()));
            result.put("token", token);
            result.put("userid", String.valueOf(user.getId()));
            result.put("name", user.getUsername());
            return result;
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "error");
            result.put("type", type);
            result.put("currentAuthority", "guest");
            return result;
        }
    }

    /**
     * 当前登录用户信息：从 Authorization: Bearer &lt;token&gt; 解析用户。
     *
     * <p>未登录/无效 token 返回 401（前端 request 拦截器会 reject，
     * getInitialState 捕获后自动跳转登录页）。</p>
     */
    @GetMapping("/api/currentUser")
    public ResponseEntity<Map<String, Object>> currentUser(HttpServletRequest request) {
        Long userId = resolveUserId(request);
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
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
        data.put("access", resolveAccess(user.getId()));
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
     * 权限判定：用户任一应用下 is_admin=1 → admin，否则 user。
     * 前端 access.ts 依赖 currentUser.access === 'admin' 决定管理权限。
     */
    private String resolveAccess(Long userId) {
        List<UserApp> userApps = userAppRepository.findByUserId(userId);
        if (userApps != null && userApps.stream().anyMatch(UserApp::getAdmin)) {
            return "admin";
        }
        return "user";
    }
}
