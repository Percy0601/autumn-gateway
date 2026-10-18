package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.User;
import xyz.wewin.autumn.gateway.dashboard.entity.UserAuthAccount;
import xyz.wewin.autumn.gateway.dashboard.repo.UserAuthAccountRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.UserRepository;

/**
 * 登录认证服务：统一认证入口，与授权服务器（JdbcUserDetailsService）使用完全相同的账号契约。
 *
 * <p>以 (identity_type, identifier) 定位认证账户，再校验 BCrypt 密码与用户状态。
 * 所有登录方式（口令 / OIDC / 短信）都走这里。</p>
 *
 * <p>注意：basic_* 新体系的用户管理在 BasicUserService；本服务仅保留登录链路依赖的
 * 旧表（user / user_auth_account）读取。待登录链路整体迁移到 basic_* 后，
 * 本类与 User / UserAuthAccount / UserApp 等旧表实体可一并删除。</p>
 */
@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthAccountRepository authAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 密码认证方式：登录查找入口 = (identity_type, identifier) */
    public static final String IDENTITY_TYPE_PASSWORD = "password";

    /**
     * 统一认证入口：与授权服务器使用完全相同的查找契约 —— (identity_type, identifier) 定位账户，
     * 再校验凭据与用户状态。
     *
     * @return 认证通过的用户
     * @throws IllegalArgumentException 账号不存在 / 密码错误 / 账号已禁用
     */
    public User authenticate(String identityType, String identifier, String credential) {
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        UserAuthAccount account = authAccountRepository
                .findByIdentityTypeAndIdentifier(identityType, identifier)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        // credential 为 null（第三方登录账户未设密码）时不允许走密码登录
        if (account.getCredential() == null
                || !passwordEncoder.matches(credential, account.getCredential())) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        User user = userRepository.findById(account.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("账号已禁用");
        }
        return user;
    }
}
