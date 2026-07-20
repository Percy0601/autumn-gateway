package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.User;
import xyz.wewin.autumn.gateway.dashboard.entity.UserAuthAccount;
import xyz.wewin.autumn.gateway.dashboard.repo.UserAuthAccountRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.UserRepository;
import xyz.wewin.autumn.gateway.dashboard.security.JwtUtil;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserAuthAccountRepository authAccountRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String identityType = body.getOrDefault("identity_type", "password");
        String identifier = body.get("identifier");
        String credential = body.get("credential");

        User user = userRepository.findByUsername(identifier)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        // 查找认证账户
        UserAuthAccount account = authAccountRepository
                .findByUserIdAndIdentityType(user.getId(), identityType)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));

        // 密码验证
        if (!passwordEncoder.matches(credential, account.getCredential())) {
            throw new IllegalArgumentException("密码错误");
        }

        // 生成 JWT
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        return Result.success(Map.of(
                "token", token,
                "user_id", user.getId(),
                "username", user.getUsername()
        ));
    }
}