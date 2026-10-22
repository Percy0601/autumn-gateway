package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
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
 *   GET  /api/currentUser     当前用户信息（凭据 = Authorization: Bearer &lt;jwt&gt;）
 *   POST /api/login/outLogin  退出登录
 * </pre>
 *
 * <h3>登录去哪了（重要）</h3>
 * 本服务<b>不签发令牌</b>：登录由网关 starter 的 {@code POST /gateway/endpoint/login}
 * 完成（含验证码校验），签发后的 JWT 由前端存进 localStorage，
 * 之后每个请求都以 {@code Authorization: Bearer <jwt>} 头带上，
 * 由 {@code JwtAuthenticationFilter} 验签。本服务不持有会话、不依赖 Cookie。
 *
 * <p>因此这里只负责「已认证之后」的用户信息与退出。</p>
 */
@RestController
public class LoginController {

    @Autowired
    private BasicUserRepository basicUserRepository;

    /**
     * 当前登录用户信息：凭据来自 {@code Authorization} 头，验签后装配出 {@link LoginUser}。
     *
     * <p>未登录 / 无效 token 返回 401（前端 request 拦截器清掉本地 token，
     * 路由守卫随后跳登录页）。</p>
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
            return unauthorized();
        }

        BasicUser user = this.basicUserRepository.findById(loginUser.getUserId()).orElse(null);
        if (user == null) {
            return unauthorized();
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
     * 退出登录：令牌无状态，服务端没有会话可清 —— 前端丢掉本地 token 即完成登出。
     * 这里只回一份协议一致的响应。
     */
    @PostMapping("/api/login/outLogin")
    public Map<String, Object> outLogin() {
        return Map.of("success", true);
    }

    private static ResponseEntity<Map<String, Object>> unauthorized() {
        // 401 只描述「没认出身份」，不再下发登录跳转地址：
        // 登录页是前端的 /login 路由，跳不跳由守卫决定。
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false,
                        "message", "未登录或登录已失效，请重新登录",
                        "data", Map.of("access", "guest")));
    }

    /**
     * 权限判定：basic_user.admin = 1 → admin，否则 user。
     * 前端 access.ts 依赖 currentUser.access === 'admin' 决定管理权限。
     */
    private String resolveAccess(BasicUser user) {
        return user.getAdmin() != null && user.getAdmin() == 1 ? "admin" : "user";
    }
}
