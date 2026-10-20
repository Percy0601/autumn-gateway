package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>连续登录失败次数（login_error_count）在此维护：密码错误 +1，登录成功清零；锁定策略后续再定。</p>
 */
@Service
@Transactional
public class UserService {

    @Autowired
    private BasicUserRepository basicUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
