package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    public BasicUser create(BasicUser user) {
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
        // 新用户未登录过、也未设置过密码
        user.setLastLoginAt(null);
        user.setPasswordUpdatedAt(null);

        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        return repository.save(user);
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

    /** 全量覆盖用户的角色关联；app_id 自动跟随角色所属应用，无需前端传 */
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
            Long appId = roleRepository.findById(roleId).map(BasicRole::getAppId).orElse(0L);
            BasicUserRole ur = new BasicUserRole();
            ur.setId(null);
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setAppId(appId);
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

    /** status: 1=正常 0=停用 */
    public BasicUser updateStatus(Long id, Integer status) {
        BasicUser existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        existing.setStatus(status);
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }
}
