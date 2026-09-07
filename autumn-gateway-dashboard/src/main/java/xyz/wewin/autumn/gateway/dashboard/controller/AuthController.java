package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.User;
import xyz.wewin.autumn.gateway.dashboard.security.JwtUtil;
import xyz.wewin.autumn.gateway.dashboard.service.UserService;

import java.util.Map;

/**
 * 内部 / 网关使用的 /auth/** 协议。
 *
 * <p>登录统一走 {@link UserService#authenticate(String, String, String)}：
 * 以 (identity_type, identifier) 为查找入口，与授权服务器保持同一套账号契约。</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String identityType = body.getOrDefault("identity_type", UserService.IDENTITY_TYPE_PASSWORD);
        String identifier = body.get("identifier");
        String credential = body.get("credential");

        try {
            User user = userService.authenticate(identityType, identifier, credential);
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());
            return Result.success(Map.of(
                    "token", token,
                    "user_id", user.getId(),
                    "uuid", user.getUuid() == null ? "" : user.getUuid(),
                    "username", user.getUsername()
            ));
        } catch (IllegalArgumentException e) {
            return Result.error(401, e.getMessage());
        }
    }
}
