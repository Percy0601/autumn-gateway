package xyz.wewin.autumn.gateway.dashboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRepository;

import java.time.LocalDateTime;

/**
 * 登录认证服务：统一认证入口。
 *
 * <p>自 v5 起，登录凭据与用户信息统一落在 basic_user（密码为 BCrypt 哈希，参考 sys_user 方式），
 * 不再依赖旧表 user / user_auth_account。授权服务器（autumn-gateway-authorization）的账号体系
 * 保持独立，待后续安全/权限方案统一时再整合。</p>
 *
 * <p>连续登录失败计数（login_error_count）在此维护：</p>
 * <ul>
 *   <li>密码错误 +1，登录成功清零；</li>
 *   <li>达到 {@code autumn.user.max-login-error-count}（默认 5）后<b>自动禁用账号</b>
 *       （status 置 0）并打 WARN 日志——账号被锁后必须由管理员在用户管理页重新启用
 *       （启用时会自动清零失败计数，避免解锁后一次错误又立刻锁定）；</li>
 *   <li>锁定后再登录（哪怕密码正确）会先被 status 检查拦下，提示"账号已禁用"。</li>
 * </ul>
 *
 * <p>验证码校验在 Controller 层（{@code LoginController}）完成，不经过本服务；
 * 内部/网关协议（{@code AuthController}）复用本服务，因此同样享有失败锁定保护。</p>
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private BasicUserRepository basicUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 连续登录失败次数上限：达到后自动禁用账号（status=0） */
    @Value("${autumn.user.max-login-error-count:5}")
    private int maxLoginErrorCount;

    /** 密码认证方式：登录查找入口 = 登录名（username） */
    public static final String IDENTITY_TYPE_PASSWORD = "password";

    /**
     * 统一认证入口：按登录名定位 basic_user，再校验 BCrypt 密码与账号状态。
     *
     * @return 认证通过的用户
     * @throws IllegalArgumentException 账号或密码错误 / 账号已禁用
     */
    public BasicUser authenticate(String identityType, String identifier, String credential) {
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        BasicUser user = basicUserRepository.findByUsername(identifier)
                .orElseThrow(() -> new IllegalArgumentException("账号或密码错误"));
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("账号已禁用");
        }
        if (user.getPassword() == null
                || !passwordEncoder.matches(credential, user.getPassword())) {
            // 仅当账号存在且密码错误时累加连续失败次数（不暴露账号是否存在）
            int next = (user.getLoginErrorCount() == null ? 0 : user.getLoginErrorCount()) + 1;
            user.setLoginErrorCount(next);
            if (next >= this.maxLoginErrorCount) {
                // 达到上限：自动禁用账号，需管理员重新启用后才能登录
                user.setStatus(0);
                log.warn("账号 {} 连续登录失败 {} 次（上限 {}），已自动禁用，需管理员重新启用",
                        identifier, next, this.maxLoginErrorCount);
            }
            basicUserRepository.save(user);
            throw new IllegalArgumentException("账号或密码错误");
        }
        // 登录成功：清零连续失败次数并刷新最近登录时间
        user.setLoginErrorCount(0);
        user.setLastLoginAt(LocalDateTime.now());
        basicUserRepository.save(user);
        return user;
    }
}
