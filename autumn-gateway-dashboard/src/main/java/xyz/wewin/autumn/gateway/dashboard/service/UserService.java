package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.dto.UserRelationRole;
import xyz.wewin.autumn.gateway.dashboard.entity.*;
import xyz.wewin.autumn.gateway.dashboard.repo.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

//import xyz.wewin.autumn.gateway.dashboard.mapper.GeneralMapper;

@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAppRepository userAppRepository;
    @Autowired
    private UserAuthAccountRepository authAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 密码认证方式：登录查找入口 = (identity_type, identifier) */
    public static final String IDENTITY_TYPE_PASSWORD = "password";

    /** 未显式指定初始密码时使用的默认密码（首次登录后应强制修改） */
    @Value("${autumn.user.default-password:Autumn@123456}")
    private String defaultPassword;
//    @Autowired
//    private GeneralMapper generalMapper;

    public Page<User> list(int page, int size, String username, String nickname, String phone) {
        long offset = (long) (page - 1) * size;
        long limit = size;
        List<User> content = userRepository.findWithPage(username, nickname, phone, offset, limit);
        long total = userRepository.countWithFilter(username, nickname, phone);
        return new PageImpl<>(content, PageRequest.of(page - 1, size), total);
    }

    public Optional<User> getById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * 创建用户：同时写入 user 与 password 认证账户，保证"建完就能登录"。
     *
     * @param user 用户信息，其中 username 必填（工号语义，创建后不可修改）；
     *             password 为可选初始密码，不传则使用 autumn.user.default-password
     */
    @Transactional
    public User create(User user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (authAccountRepository.findByIdentityTypeAndIdentifier(IDENTITY_TYPE_PASSWORD, user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("该登录标识已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        String rawPassword = (user.getPassword() == null || user.getPassword().isBlank())
                ? defaultPassword : user.getPassword();

        user.setId(null);
        user.setUuid(UUID.randomUUID().toString());
        user.setPasswordUpdatedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        User saved = userRepository.save(user);

        UserAuthAccount account = new UserAuthAccount();
        account.setUserId(saved.getId());
        account.setIdentityType(IDENTITY_TYPE_PASSWORD);
        account.setIdentifier(saved.getUsername());
        account.setCredential(passwordEncoder.encode(rawPassword));
        account.setVerified(true);
        account.setCredentialUpdatedAt(now);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        authAccountRepository.save(account);

        // 明文密码仅用于入参，绝不回传前端
        saved.setPassword(null);
        return saved;
    }

    /**
     * 更新用户：username 属工号语义，接口层面禁止修改（需变更请后台改库并同步 user_auth_account.identifier）
     */
    public User update(Long id, User req) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (req.getUsername() != null && !req.getUsername().equals(existing.getUsername())) {
            throw new IllegalArgumentException("用户名不可修改，如需变更请联系管理员后台处理");
        }
        if (req.getNickname() != null) existing.setNickname(req.getNickname());
        if (req.getEmail() != null) existing.setEmail(req.getEmail());
        if (req.getPhone() != null) existing.setPhone(req.getPhone());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(existing);
    }

    public void disable(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setStatus(0);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void enable(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setStatus(1);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    // ---- 用户-应用关联 ----
    public List<UserApp> getUserApps(Long userId) {
        return userAppRepository.findByUserId(userId);
    }

    public void saveUserApps(Long userId, List<UserApp> apps) {
        userAppRepository.deleteByUserId(userId);
        apps.forEach(app -> {
            app.setUserId(userId);
            app.setCreatedAt(LocalDateTime.now());
            userAppRepository.save(app);
        });
    }

    // ---- 认证账户（只读 + 重置密码） ----
    public List<UserAuthAccount> getAuthAccounts(Long userId) {
        return authAccountRepository.findByUserId(userId);
    }

    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        // 对密码进行 BCrypt 加密
        authAccountRepository.updatePassword(userId, passwordEncoder.encode(newPassword));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * 统一认证入口：与授权服务器使用完全相同的查找契约 —— (identity_type, identifier) 定位账户，
     * 再校验凭据与用户状态。所有登录方式（口令 / OIDC / 短信）都走这里。
     *
     * @return 认证通过的用户
     * @throws IllegalArgumentException 账号不存在 / 密码错误 / 账号已禁用
     */
    public User authenticate(String identityType, String identifier, String credential) {
        UserAuthAccount account = authAccountRepository
                .findByIdentityTypeAndIdentifier(identityType, identifier)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));

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

    /**
     * 获取用户的角色ID列表（按应用分组，或直接返回所有）
     */
    public List<UserRole> getUserRoles(Long userId) {
        return userRoleRepository.findByUserId(userId);
    }

    /**
     * 设置用户的角色（全量覆盖，需要指定应用和授权人）
     */
    public void setUserRoles(Long userId, Long createdBy, List<Long> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        roleIds.forEach(roleId -> {
            Role role = roleRepository.findById(roleId)
                    .orElse(null);
            if(Objects.isNull(role)) {
                return;
            }
            Long appId = role.getAppId();

            userRoleRepository.insert(userId, roleId, appId, createdBy);
        });
    }


    /**
     * 1. 更新用户关联的应用（先删后插）
     */
    public void updateUserApps(Long userId, List<Long> appIds) {
        userAppRepository.deleteByUserId(userId);
        if (appIds != null && !appIds.isEmpty()) {
            List<UserApp> list = appIds.stream()
                    .map(appId -> {
                        UserApp ua = new UserApp();
                        ua.setUserId(userId);
                        ua.setAppId(appId);
                        ua.setCreatedAt(LocalDateTime.now());
                        return ua;
                    })
                    .collect(Collectors.toList());
            userAppRepository.saveAll(list);
        }
    }

    /**
     * 2. 更新用户关联的角色（先删后插）
     */
    public void updateUserRoles(Long userId, List<Long> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            List<UserRole> list = roleIds.stream()
                    .map(roleId -> {
                        UserRole ur = new UserRole();
                        ur.setUserId(userId);
                        ur.setRoleId(roleId);
                        ur.setCreatedAt(LocalDateTime.now());
                        return ur;
                    })
                    .collect(Collectors.toList());
            userRoleRepository.saveAll(list);
        }
    }

    /**
     * 3. 查询该用户下所有角色（返回角色对象列表）
     */
    public List<Role> listRolesByUserId(Long userId) {
        List<Long> roleIds = userRoleRepository.findByUserId(userId)
                .stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());
        if (roleIds.isEmpty())
            return Collections.emptyList();
        List<Role> roles = null;
        roleRepository.findAllById(roleIds)
                .forEach(roles::add);
        return roles;
    }

    public Page<UserRelationRole> findRelationRoles(Long appId,
                                                    Long userId,
                                                    int current,
                                                    int pageSize) {
        long total = userRoleRepository.countByAppIdAndUserId(appId, userId);
        long limit = (long) (current - 1) * pageSize;
        long offset = pageSize;
        List<UserRole> content = userRoleRepository.findByAppIdAndUserId(appId,
                userId,
                limit,
                offset);
        List<UserRelationRole> result = new ArrayList<>();
        content.forEach(it -> {
            UserRelationRole userRelationRole = new UserRelationRole();
            userRelationRole.setAppId(it.getAppId());
            userRelationRole.setUserId(it.getUserId());
            Role role = roleRepository.findById(it.getRoleId()).orElse(null);
            if (role != null) {
                userRelationRole.setName(role.getName());
                userRelationRole.setCode(role.getCode());
                userRelationRole.setStatus(role.getStatus());
            }
            result.add(userRelationRole);

            if(null == it.getUserId()) {
                userRelationRole.setRelationStatus(0);
            } else {
                userRelationRole.setRelationStatus(1);
            }
            Application application = applicationRepository.findById(it.getAppId()).orElse(null);
            if (application != null) {
                userRelationRole.setAppName(application.getName());
            }
        });
        return new PageImpl<>(result, PageRequest.of(current - 1, pageSize), total);
    }

    public List<User> listAll() {
        List<User> users = new ArrayList<>();
        userRepository.findAll().forEach(users::add);
        return users;
    }
}
