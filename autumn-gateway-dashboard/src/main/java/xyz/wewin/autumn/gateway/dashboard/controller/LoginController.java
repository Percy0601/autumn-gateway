package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRepository;
import xyz.wewin.autumn.gateway.dashboard.security.LoginUser;
import xyz.wewin.autumn.gateway.dashboard.security.Permissions;

import java.util.HashMap;
import java.util.Map;

/**
 * 前端页面协议（ant-design-pro 官方模板）：
 *
 * <pre>
 *   GET  /api/currentUser     当前用户信息（凭据由网关透传）
 *   POST /api/login/outLogin  退出登录
 * </pre>
 *
 * <h3>登录去哪了（重要）</h3>
 * 本服务<b>不再签发令牌</b>：令牌统一由 {@code autumn-gateway-authorization} 以 RS256 签发，
 * 网关（{@code autumn-gateway-deploy}）作为 OAuth2 客户端完成登录后，
 * 通过 {@code TokenRelay} 把 access_token 以 {@code Authorization: Bearer} 注入转发请求。
 * 浏览器侧只持有网关的会话 Cookie，拿不到也存不下令牌。
 *
 * <p>因此登录入口是网关的 {@code /oauth2/authorization/{registrationId}}，
 * 本 Controller 只负责「已认证之后」的用户信息与退出。</p>
 */
@RestController
public class LoginController {

    @Autowired
    private BasicUserRepository basicUserRepository;

    /** 网关 OAuth2 登录入口，返回给前端做跳转用（前端直接用相对路径即可） */
    @Value("${autumn.auth.login-url:/oauth2/authorization/web-client}")
    private String oauthLoginUrl;

    /** 网关登出入口 */
    @Value("${autumn.auth.logout-url:/logout}")
    private String oauthLogoutUrl;

    /**
     * 当前登录用户信息：凭据由网关透传，本服务验签后装配出 {@link LoginUser}。
     *
     * <p>未登录/无效 token 返回 401（前端 request 拦截器会 reject，
     * getInitialState 捕获后自动跳转登录页）。</p>
     *
     * <p>下发字段：
     * <ul>
     *   <li>{@code access}：保留原有 admin/user 语义，前端 access.ts 仍在用；</li>
     *   <li>{@code authorityList}：按钮级权限标识清单，前端 v-auth 指令据此显隐。
     *       超管下发 {@code ["*"]}，前端按通配处理。</li>
     * </ul>
     */
    @GetMapping("/api/currentUser")
    public ResponseEntity<Map<String, Object>> currentUser(
            @AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            // 没有认证态：告诉前端去哪儿登录，避免前端只能干瞪眼跳登录页
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false,
                            "data", Map.of("access", "guest"),
                            "loginUrl", this.oauthLoginUrl));
        }

        BasicUser user = this.basicUserRepository.findById(loginUser.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false,
                            "data", Map.of("access", "guest"),
                            "loginUrl", this.oauthLoginUrl));
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
        data.put("authorityList", Permissions.currentAuthorities());
        data.put("superAdmin", loginUser.isSuperAdmin());
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    /**
     * 退出登录：真正的登出在网关（清会话 + 通知授权服务器）。
     * 这里只回一份协议一致的响应，并告诉前端下一步跳哪里。
     */
    @PostMapping("/api/login/outLogin")
    public Map<String, Object> outLogin() {
        return Map.of("success", true, "data", Map.of("logoutUrl", this.oauthLogoutUrl));
    }

    /**
     * 权限判定：basic_user.admin = 1 → admin，否则 user。
     * 前端 access.ts 依赖 currentUser.access === 'admin' 决定管理权限。
     */
    private String resolveAccess(BasicUser user) {
        return user.getAdmin() != null && user.getAdmin() == 1 ? "admin" : "user";
    }
}
