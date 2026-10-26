package xyz.wewin.autumn.gateway.dashboard.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRoleRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 账号密码登录：验证码校验 → basic_user 校验 → BCrypt 比对 → 签发 JWT。
 *
 * <p>与网关 authz starter 的 {@code AuthzLoginService} 保持同一套行为契约
 * （失败消息、失败计数、锁定策略、成功恢复计数），差异只在：</p>
 * <ul>
 *   <li>验证码答案由本服务内存的 {@link CaptchaService} 关联 —— {@code /api/captcha}
 *       与 {@code /api/login/account} 同源同机，captchaId 不再跨服务查不到答案
 *       （此前登录走网关时，网关用自己的内存校验 dashboard 下发的 captchaId，必然报
 *       「验证码错误或已过期」）；</li>
 *   <li>令牌由本服务用同一把共享密钥（{@code autumn.security.jwt.secret} /
 *       {@code AUTUMN_SHARED_JWT_SECRET}）签发，网关与下游都按同一 HS256 轨道验签。</li>
 * </ul>
 *
 * <p>失败计数（防暴力破解，与网关一致）：</p>
 * <ul>
 *   <li>密码错误：{@code login_error_count - 1}；减到 0 时把 {@code status} 置 0（账号锁定）；</li>
 *   <li>登录成功：次数恢复为 {@code autumn.user.max-login-error-count}，并刷新 {@code last_login_at}；</li>
 *   <li>用户不存在时不写任何更新 —— 否则「用户名是否存在」会被枚举出来（账号枚举攻击）。</li>
 * </ul>
 */
@Service
public class DashboardLoginService {

    private static final Logger log = LoggerFactory.getLogger(DashboardLoginService.class);

    private final BasicUserRepository userRepository;
    private final BasicRoleRepository roleRepository;
    private final CaptchaService captchaService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final int maxLoginErrorCount;
    private final String loginAppCode;

    public DashboardLoginService(BasicUserRepository userRepository,
                                 BasicRoleRepository roleRepository,
                                 CaptchaService captchaService,
                                 JwtTokenService jwtTokenService,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${autumn.user.max-login-error-count:5}") int maxLoginErrorCount,
                                 @Value("${basic.rbac.app-code:dashboard}") String loginAppCode) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.captchaService = captchaService;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.maxLoginErrorCount = maxLoginErrorCount > 0 ? maxLoginErrorCount : 5;
        this.loginAppCode = StringUtils.hasText(loginAppCode) ? loginAppCode : "dashboard";
    }

    /**
     * @param captchaId 验证码标识（优先取 {@code /api/captcha} 下发的 HttpOnly Cookie，请求体字段仅作旧流程兼容）
     * @param captcha   用户输入的验证码（大小写不敏感）
     */
    public LoginResult login(String username, String rawPassword, String captchaId, String captcha) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(rawPassword)) {
            return LoginResult.failed("用户名或密码不能为空");
        }

        String captchaError = checkCaptcha(captchaId, captcha);
        if (captchaError != null) {
            return LoginResult.failed(captchaError);
        }

        Optional<BasicUser> found = this.userRepository.findByUsername(username.trim());
        if (found.isEmpty()) {
            // 用户不存在：不更新任何行，也不告诉调用方「用户名不存在」（防账号枚举）
            return LoginResult.failed("用户名或密码错误");
        }

        BasicUser user = found.get();
        if (user.getStatus() == null || user.getStatus() != 1) {
            return LoginResult.failed("账号已被锁定，请联系管理员");
        }

        if (!StringUtils.hasText(user.getPassword())
                || !this.passwordEncoder.matches(rawPassword, user.getPassword())) {
            return onPasswordMismatch(user);
        }

        return onSuccess(user);
    }

    /** 验证码校验：关闭开关时直接通过，便于灰度关闭而前端无需改动 */
    private String checkCaptcha(String captchaId, String input) {
        if (!this.captchaService.isEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(input)) {
            return "请输入验证码";
        }
        if (!this.captchaService.validate(captchaId, input)) {
            return "验证码错误或已过期";
        }
        return null;
    }

    private LoginResult onPasswordMismatch(BasicUser user) {
        int currentRemaining = Math.min(Optional.ofNullable(user.getLoginErrorCount()).orElse(this.maxLoginErrorCount),
                this.maxLoginErrorCount);
        int nextRemaining = Math.max(0, currentRemaining - 1);
        Integer nextStatus = (nextRemaining <= 0) ? 0 : user.getStatus();
        user.setLoginErrorCount(nextRemaining);
        user.setStatus(nextStatus);
        user.setUpdateTime(LocalDateTime.now());
        this.userRepository.save(user);
        return nextRemaining <= 0
                ? LoginResult.failed("密码错误次数过多，账号已被锁定")
                : LoginResult.failed("用户名或密码错误，还可尝试 " + nextRemaining + " 次");
    }

    private LoginResult onSuccess(BasicUser user) {
        boolean admin = isAdmin(user);
        List<String> roleCodes = this.roleRepository.findRoleCodesByUserIdAndAppCode(user.getId(), this.loginAppCode);
        List<String> authorities = List.copyOf(roleCodes);
        try {
            String token = this.jwtTokenService.issue(user.getUuid(), user.getId(), user.getUsername(),
                    authorities, admin);
            // 成功：恢复失败计数并刷新最近登录时间（与网关 login-success-sql 一致）
            user.setLoginErrorCount(this.maxLoginErrorCount);
            user.setLastLoginAt(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            this.userRepository.save(user);
            return LoginResult.ok(token, user.getId(), user.getUsername(), authorities, admin);
        } catch (IllegalStateException ex) {
            // 共享密钥未配置时无法签发（启动期验签轨道可能只剩 JWKS）
            log.error("登录成功但签发令牌失败：{}", ex.getMessage());
            return LoginResult.failed("登录服务配置异常，请联系管理员");
        }
    }

    private static boolean isAdmin(BasicUser user) {
        return user.getAdmin() != null && user.getAdmin() == 1;
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
    public record LoginResult(boolean ok, String message, String token, Long userId,
                              String username, List<String> authorities, boolean admin) {

        public static LoginResult ok(String token, Long userId, String username,
                                     List<String> authorities, boolean admin) {
            return new LoginResult(true, null, token, userId, username, authorities, admin);
        }

        public static LoginResult failed(String message) {
            return new LoginResult(false, message, null, null, null, List.of(), false);
        }
    }
}
