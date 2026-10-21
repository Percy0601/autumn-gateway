package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicGroup;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUserGroup;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUserRole;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRoleRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRoleRepository;
import xyz.wewin.autumn.gateway.dashboard.security.RequestContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import xyz.wewin.autumn.gateway.dashboard.security.GrantChanged;

/**
 * 基础用户服务，基于 basic_user，并维护 basic_user_group（用户-用户组关联）。
 *
 * <p>basic_user 已整合旧 user 主表字段（uuid / username / nickname / avatar /
 * email / phone / status / password_updated_at / last_login_at）。
 *
 * <p>约定：username 与 uuid 创建后禁止修改（update 时忽略传入值），
 * 需要变更只能后台改库。
 */
@Service
@Transactional
public class BasicUserService {

    @Autowired
    private BasicUserRepository repository;

    @Autowired
    private BasicUserGroupRepository userGroupRepository;

    @Autowired
    private BasicGroupRepository groupRepository;

    @Autowired
    private BasicRoleRepository roleRepository;

    @Autowired
    private BasicUserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 管理员重置密码时，新密码的最短长度 */
    private static final int MIN_PASSWORD_LENGTH = 8;

    /** 管理员重置密码时未指定新密码，则回落到系统默认密码 */
    private final String defaultPassword;

    /** 登录允许的剩余重试次数（满额值）：新用户初始、改密后、管理员启用时都回填为该值 */
    private final int maxLoginErrorCount;

    public BasicUserService(@Value("${autumn.user.default-password:Autumn@123456}") String defaultPassword,
                            @Value("${autumn.user.max-login-error-count:5}") int maxLoginErrorCount) {
        this.defaultPassword = defaultPassword;
        this.maxLoginErrorCount = maxLoginErrorCount;
    }

    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    /** 空集合用 {-1} 占位，避免 SQL 出现 IN () */
    private static List<Long> normalizeBoundIds(List<Long> boundIds) {
        return (boundIds == null || boundIds.isEmpty()) ? List.of(-1L) : boundIds;
    }

    /** 分页列表；status 传 null 或 -1 表示不过滤 */
    public Page<BasicUser> list(String empNo, String username, String phone, Integer status,
                                List<Long> boundIds, int current, int size) {
        int statusFilter = (status == null) ? -1 : status;
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<Long> bound = normalizeBoundIds(boundIds);
        List<BasicUser> content = repository.findWithPage(
                nvl(empNo), nvl(username), nvl(phone), statusFilter, bound, offset, (long) size);
        long total = repository.countWithFilter(
                nvl(empNo), nvl(username), nvl(phone), statusFilter, bound);
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<BasicUser> getById(Long id) {
        return repository.findById(id);
    }

    /**
     * 新增用户。
     *
     * <p>密码由<b>后台下发</b>：请求未指定 password 时使用系统默认密码
     * {@code autumn.user.default-password}（默认 {@code Autumn@123456}），
     * 库中只存 BCrypt 哈希，<b>明文</b>随返回值一次性回给调用方，供弹窗转告用户。
     *
     * @param user 待创建用户；password 留空表示使用系统默认密码
     * @return {@code id / username / password(明文)}
     */
    public Map<String, Object> create(BasicUser user) {
        if (user.getEmpNo() == null || user.getEmpNo().isBlank()) {
            throw new IllegalArgumentException("员工工号不能为空");
        }
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new IllegalArgumentException("登录名不能为空");
        }
        if (repository.findByEmpNo(user.getEmpNo()).isPresent()) {
            throw new IllegalArgumentException("员工工号已存在: " + user.getEmpNo());
        }
        if (repository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("登录名已存在: " + user.getUsername());
        }
        user.setId(null);
        // uuid 为对外统一标识，未指定时自动生成，生成后永不变化
        if (user.getUuid() == null || user.getUuid().isBlank()) {
            user.setUuid(UUID.randomUUID().toString());
        }
        if (user.getNickname() == null) user.setNickname("");
        if (user.getAvatar() == null) user.setAvatar("");
        if (user.getEmail() == null) user.setEmail("");
        if (user.getPhone() == null) user.setPhone("");
        if (user.getAdmin() == null) user.setAdmin(0);
        if (user.getStatus() == null) user.setStatus(1);
        if (user.getLatestRoleId() == null) user.setLatestRoleId(0L);
        // 登录重试次数给满额，与建表默认值一致
        if (user.getLoginErrorCount() == null) user.setLoginErrorCount(maxLoginErrorCount);
        // 新用户未登录过
        user.setLastLoginAt(null);

        // 密码：未显式指定则用系统默认密码，库里只留哈希
        String plain = (user.getPassword() == null || user.getPassword().isBlank())
                ? defaultPassword
                : user.getPassword().trim();
        if (plain.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位");
        }
        LocalDateTime now = LocalDateTime.now();
        user.setPassword(passwordEncoder.encode(plain));
        user.setPasswordUpdatedAt(now);

        user.setCreateTime(now);
        user.setUpdateTime(now);
        BasicUser saved = repository.save(user);

        Map<String, Object> created = new HashMap<>(4);
        created.put("id", saved.getId());
        created.put("username", saved.getUsername());
        created.put("password", plain);
        return created;
    }

    public BasicUser update(Long id, BasicUser req) {
        BasicUser existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        if (req.getEmpNo() != null) existing.setEmpNo(req.getEmpNo());
        if (req.getNickname() != null) existing.setNickname(req.getNickname());
        if (req.getAvatar() != null) existing.setAvatar(req.getAvatar());
        if (req.getEmail() != null) existing.setEmail(req.getEmail());
        if (req.getPhone() != null) existing.setPhone(req.getPhone());
        if (req.getAdmin() != null) existing.setAdmin(req.getAdmin());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getLatestRoleId() != null) existing.setLatestRoleId(req.getLatestRoleId());
        // username / uuid 创建后禁止修改，忽略传入值
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    /** 记录一次登录：刷新最近登录时间与最近使用角色（供网关鉴权成功后调用） */
    public void recordLogin(Long id, Long latestRoleId) {
        BasicUser existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        existing.setLastLoginAt(LocalDateTime.now());
        if (latestRoleId != null) existing.setLatestRoleId(latestRoleId);
        repository.save(existing);
    }

    @GrantChanged
    public void remove(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("用户不存在: " + id);
        }
        // 先清理关联，避免留下孤儿数据
        userGroupRepository.findByUserId(id).forEach(userGroupRepository::delete);
        userRoleRepository.findByUserId(id).forEach(userRoleRepository::delete);
        repository.deleteById(id);
    }

    // ---------- 用户 - 用户组关联（basic_user_group） ----------

    /** 查询用户已关联的用户组 */
    public List<BasicGroup> getGroups(Long userId) {
        List<Long> groupIds = userGroupRepository.findByUserId(userId).stream()
                .filter(ug -> ug.getDeleted() == null || ug.getDeleted() == 0)
                .map(BasicUserGroup::getGroupId)
                .distinct()
                .toList();
        if (groupIds.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicGroup>) groupRepository.findAllById(groupIds);
    }

    /** 全量覆盖用户的用户组关联 */
    @GrantChanged
    public void setGroups(Long userId, List<Long> groupIds) {
        if (!repository.existsById(userId)) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        userGroupRepository.findByUserId(userId).forEach(userGroupRepository::delete);
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long groupId : groupIds) {
            if (groupId == null) continue;
            BasicUserGroup ug = new BasicUserGroup();
            ug.setId(null);
            ug.setUserId(userId);
            ug.setGroupId(groupId);
            ug.setDeleted(0);
            ug.setCreateTime(now);
            ug.setUpdateTime(now);
            userGroupRepository.save(ug);
        }
    }

    // ---------- 用户 - 角色关联（basic_user_role） ----------

    /** 查询用户已直接关联的角色（不含通过用户组间接继承的角色） */
    public List<BasicRole> getRoles(Long userId) {
        List<Long> roleIds = userRoleRepository.findByUserId(userId).stream()
                .filter(ur -> ur.getDelFlag() == null || ur.getDelFlag() == 0)
                .map(BasicUserRole::getRoleId)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicRole>) roleRepository.findAllById(roleIds);
    }

    /** 全量覆盖用户的角色关联；角色已跨应用，用户-角色关联的 app_id 统一置 0（命名空间级） */
    @GrantChanged
    public void setRoles(Long userId, List<Long> roleIds) {
        if (!repository.existsById(userId)) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        userRoleRepository.findByUserId(userId).forEach(userRoleRepository::delete);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long opUser = RequestContext.currentUserId();
        for (Long roleId : roleIds) {
            if (roleId == null) continue;
            BasicUserRole ur = new BasicUserRole();
            ur.setId(null);
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setAppId(0L);
            ur.setDelFlag(0);
            ur.setCreateUser(opUser);
            ur.setUpdateUser(opUser);
            ur.setCreateTime(now);
            ur.setUpdateTime(now);
            // remark 在库里是 NOT NULL DEFAULT ''
            ur.setRemark("");
            userRoleRepository.save(ur);
        }
    }

    /**
     * 管理员重置用户密码（高危操作，需 {@code system:user:reset-pwd} 权限）。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>{@code newPassword} 为空 → 回落到系统默认密码 {@code autumn.user.default-password}；</li>
     *   <li>无论哪种情况都会刷新 {@code password_updated_at}，便于审计「上次改密时间」；</li>
     *   <li>把 {@code login_error_count} 恢复满额：账号常常是因为重试次数耗尽被自动禁用
     *       才来找管理员的，改了密码却仍被锁着会让用户以为重置失败；</li>
     *   <li>{@code unlock=true} 时顺带把 {@code status} 置回 1（启用）。</li>
     * </ul>
     *
     * <p><b>返回值为什么带明文：</b>管理员要把临时密码转告用户，返回明文是这个场景的刚需。
     * 只在本次响应里出现一次，不落库、不打日志（调用方请勿记录）。</p>
     *
     * <p><b>说明：</b>本模块的两个登录入口（{@code /api/login/account} 与 {@code /auth/login}）
     * 都走 {@code basic_user}，因此只需改这一张表。授权服务器模块
     * （autumn-gateway-authorization）用的是自己的 {@code user_auth_account}，
     * 与本表无关，不在本次范围内。</p>
     *
     * <p><b>已知限制：</b>JWT 无状态，重置密码无法让旧 token 立即失效，
     * 旧 token 最长 {@code jwt.expiration-ms} 后自然过期。若要求「改密即踢下线」，
     * 需要引入 token 版本号或黑名单，本期不做。</p>
     *
     * @param id          用户 id
     * @param newPassword 新密码明文；null/空白时回落默认密码
     * @param unlock      是否同时解除禁用状态
     * @return 实际生效的明文密码
     */
    public String resetPassword(Long id, String newPassword, boolean unlock) {
        BasicUser existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));

        String plain = (newPassword == null || newPassword.isBlank())
                ? defaultPassword
                : newPassword.trim();
        if (plain.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位");
        }

        LocalDateTime now = LocalDateTime.now();
        existing.setPassword(passwordEncoder.encode(plain));
        existing.setPasswordUpdatedAt(now);
        // 改密必然伴随解锁诉求：被自动锁定的账号重置后应能立刻登录
        existing.setLoginErrorCount(maxLoginErrorCount);
        if (unlock) {
            existing.setStatus(1);
        }
        existing.setUpdateTime(now);
        repository.save(existing);
        return plain;
    }

    /** status: 1=正常 0=停用 */
    public BasicUser updateStatus(Long id, Integer status) {
        BasicUser existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        existing.setStatus(status);
        // 重新启用时恢复满额重试次数：账号可能因次数耗尽被自动锁定，
        // 不恢复的话启用后一次错误又会立刻锁定
        if (Integer.valueOf(1).equals(status)) {
            existing.setLoginErrorCount(maxLoginErrorCount);
        }
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }
}
