package xyz.wewin.autumn.gateway.authz.web;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import xyz.wewin.autumn.gateway.authz.autoconfigure.AuthzGatewayProperties;
import xyz.wewin.autumn.gateway.authz.jwt.AuthzJwtService;
import xyz.wewin.autumn.gateway.authz.rbac.AuthzRbacRepository;
import xyz.wewin.autumn.gateway.authz.rbac.AuthzRole;
import xyz.wewin.autumn.gateway.authz.rbac.AuthzUserRecord;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 用户名 + 密码登录：JDBC 查 basic_user → BCrypt 校验 → 签发 JWT。
 *
 * <h3>失败计数（防暴力破解）</h3>
 * <ul>
 *   <li>密码错误：{@code login_error_count - 1}；减到 0 时把 {@code status} 置 0（账号锁定）；</li>
 *   <li>登录成功：次数恢复为 {@code max-login-attempts}，并刷新 {@code last_login_at}；</li>
 *   <li><b>用户不存在时不写任何更新</b>——否则「用户名是否存在」会被枚举出来（账号枚举攻击）；</li>
 *   <li>计数写入失败时本次登录按服务不可用处理，不能静默放过，否则会绕过锁定保护。</li>
 * </ul>
 *
 * <p>密码哈希用 BCrypt（与 dashboard 保持一致）。BCrypt 的 cost 是哈希里自带的，
 * 不需要额外配置；这里只做 {@code matches}，不负责加密存储。</p>
 */
public final class AuthzLoginService {

    private final AuthzRbacRepository repository;
    private final AuthzJwtService jwtService;
    private final AuthzGatewayProperties properties;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthzLoginService(AuthzRbacRepository repository,
                             AuthzJwtService jwtService,
                             AuthzGatewayProperties properties) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    /**
     * @return {@link LoginResult#ok()} 为 null 表示失败（见 {@link LoginResult#message()}）
     */
    public Mono<LoginResult> login(String username, String rawPassword) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(rawPassword)) {
            return Mono.just(LoginResult.failed("用户名或密码不能为空"));
        }
        // findUser 查不到时是「空 Mono」而不是发射 null，因此先包成 Optional 再判空
        return this.repository.findUser(username)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(found -> {
                    if (found.isEmpty()) {
                        // 用户不存在：不更新任何行，也不告诉调用方「用户名不存在」（防账号枚举）
                        return Mono.just(LoginResult.failed("用户名或密码错误"));
                    }
                    AuthzUserRecord user = found.get();
                    if (user.status() != 1) {
                        return Mono.just(LoginResult.failed("账号已被锁定，请联系管理员"));
                    }
                    if (user.passwordHash() == null || user.passwordHash().isBlank()
                            || !this.passwordEncoder.matches(rawPassword, user.passwordHash())) {
                        return onPasswordMismatch(user);
                    }
                    return onSuccess(user);
                })
                .onErrorResume(error -> Mono.just(LoginResult.failed("登录服务暂时不可用")));
    }

    private Mono<LoginResult> onPasswordMismatch(AuthzUserRecord user) {
        int currentRemaining = Math.min(user.loginErrorCount(), this.properties.getMaxLoginAttempts());
        int nextRemaining = Math.max(0, currentRemaining - 1);
        int nextStatus = (nextRemaining <= 0) ? 0 : user.status();
        return this.repository.recordFailure(user.userId(), nextRemaining, nextStatus)
                .then(Mono.just(nextRemaining <= 0
                        ? LoginResult.failed("密码错误次数过多，账号已被锁定")
                        : LoginResult.failed("用户名或密码错误，还可尝试 " + nextRemaining + " 次")));
    }

    private Mono<LoginResult> onSuccess(AuthzUserRecord user) {
        return this.repository.findRoles(user.userId(), this.properties.getLoginAppCode())
                .defaultIfEmpty(List.of())
                .flatMap(roles -> this.repository.recordSuccess(user.userId(), this.properties.getMaxLoginAttempts())
                        .then(Mono.just(buildResult(user, roles))));
    }

    private LoginResult buildResult(AuthzUserRecord user, List<AuthzRole> roles) {
        Set<String> roleCodes = new LinkedHashSet<>();
        for (AuthzRole role : roles) {
            if (StringUtils.hasText(role.code())) {
                roleCodes.add(role.code());
            }
        }
        List<String> authorities = List.copyOf(roleCodes);
        String token = this.jwtService.issue(user.subject(),
                String.valueOf(user.userId()),
                user.username(),
                authorities,
                user.admin());
        return LoginResult.ok(token, String.valueOf(user.userId()), user.username(), authorities, user.admin());
    }

    /**
     * 登录结果。
     *
     * @param ok          true 表示成功
     * @param message     失败原因（成功时为 null）
     * @param token       成功时签发的 JWT（裸令牌，不带 Bearer 前缀）
     * @param userId      basic_user.id
     * @param username    登录名
     * @param authorities 角色编码集合
     * @param admin       是否管理员
     */
    public record LoginResult(boolean ok, String message, String token, String userId,
                              String username, List<String> authorities, boolean admin) {

        public static LoginResult ok(String token, String userId, String username,
                                     List<String> authorities, boolean admin) {
            return new LoginResult(true, null, token, userId, username, authorities, admin);
        }

        public static LoginResult failed(String message) {
            return new LoginResult(false, message, null, null, null, List.of(), false);
        }
    }
}
