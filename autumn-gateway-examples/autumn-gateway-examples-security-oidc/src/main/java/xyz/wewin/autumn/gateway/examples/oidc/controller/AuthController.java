package xyz.wewin.autumn.gateway.examples.oidc.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.examples.oidc.util.JwtUtils;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody LoginRequest request) {
        // 1. 校验用户名密码（实际项目中查数据库）
        if (!"admin".equals(request.username()) || !"123456".equals(request.password())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 2. 生成 JWT
        String token = JwtUtils.generateToken(
                request.username(),
                "123", // userId
                List.of("ADMIN", "USER") // roles
        );

        // 3. 返回 Token
        return Map.of("token", token, "type", "Bearer");
    }
}

record LoginRequest(String username, String password) {}