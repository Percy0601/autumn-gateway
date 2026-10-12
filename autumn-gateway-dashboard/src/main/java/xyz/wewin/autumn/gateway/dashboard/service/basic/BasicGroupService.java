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
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicGroupGroup;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRoleGroup;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUserGroup;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicGroupGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRoleGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRoleRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRepository;
import xyz.wewin.autumn.gateway.dashboard.security.RequestContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 用户组服务，基于 basic_group，并维护 basic_role_group（用户组-角色关联）。
 */
@Service
@Transactional
public class BasicGroupService {

    @Autowired
    private BasicGroupRepository repository;

    @Autowired
    private BasicRoleGroupRepository roleGroupRepository;

    @Autowired
    private BasicRoleRepository roleRepository;

    @Autowired
    private BasicUserGroupRepository userGroupRepository;

    @Autowired
    private BasicUserRepository userRepository;

    @Autowired
    private BasicGroupGroupRepository groupGroupRepository;

    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    public Page<BasicGroup> list(String name, String code, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<BasicGroup> content = repository.findWithPage(nvl(name), nvl(code), offset, (long) size);
        long total = repository.countWithFilter(nvl(name), nvl(code));
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<BasicGroup> getById(Long id) {
        return repository.findById(id);
    }

    public BasicGroup create(BasicGroup group) {
        if (group.getName() == null || group.getName().isBlank()) {
            throw new IllegalArgumentException("岗位名称不能为空");
        }
        group.setId(null);
        if (group.getDeleted() == null) group.setDeleted(0);
        if (group.getCode() == null) group.setCode("");
        if (group.getOrgId() == null) group.setOrgId(0L);
        if (group.getJobId() == null) group.setJobId(0L);
        group.setCreateTime(LocalDateTime.now());
        group.setUpdateTime(LocalDateTime.now());
        return repository.save(group);
    }

    public BasicGroup update(Long id, BasicGroup req) {
        BasicGroup existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户组不存在: " + id));
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getCode() != null) existing.setCode(req.getCode());
        if (req.getOrgId() != null) existing.setOrgId(req.getOrgId());
        if (req.getJobId() != null) existing.setJobId(req.getJobId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    /** 软删除：deleted = 1，同时解除与其它用户组的上下级关联 */
    public void remove(Long id) {
        BasicGroup existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户组不存在: " + id));
        existing.setDeleted(1);
        existing.setUpdateTime(LocalDateTime.now());
        repository.save(existing);
        groupGroupRepository.findByParentGroupId(id).forEach(groupGroupRepository::delete);
        groupGroupRepository.findByGroupId(id).forEach(groupGroupRepository::delete);
    }

    public List<BasicGroup> listAll() {
        return (List<BasicGroup>) repository.findAll();
    }

    // ---------- 用户组 - 角色关联（basic_role_group） ----------

    /** 查询用户组已关联的角色 */
    public List<BasicRole> getRoles(Long groupId) {
        List<Long> roleIds = roleGroupRepository.findByGroupId(groupId).stream()
                .filter(rg -> rg.getDeleted() == null || rg.getDeleted() == 0)
                .map(BasicRoleGroup::getRoleId)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicRole>) roleRepository.findAllById(roleIds);
    }

    /** 全量覆盖用户组的角色关联 */
    public void setRoles(Long groupId, List<Long> roleIds) {
        if (!repository.existsById(groupId)) {
            throw new IllegalArgumentException("用户组不存在: " + groupId);
        }
        roleGroupRepository.findByGroupId(groupId).forEach(roleGroupRepository::delete);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : roleIds) {
            if (roleId == null) continue;
            BasicRoleGroup rg = new BasicRoleGroup();
            rg.setId(null);
            rg.setGroupId(groupId);
            rg.setRoleId(roleId);
            rg.setDeleted(0);
            // basic_role_group.op_user_id 为 NOT NULL
            rg.setOpUserId(RequestContext.currentUserId());
            rg.setCreateTime(now);
            rg.setUpdateTime(now);
            roleGroupRepository.save(rg);
        }
    }

    // ---------- 用户组 - 用户关联（basic_user_group） ----------

    /** 查询用户组已关联的用户（组视角，对应 /group/{id}/users） */
    public List<BasicUser> getUsers(Long groupId) {
        List<Long> userIds = userGroupRepository.findByGroupId(groupId).stream()
                .filter(ug -> ug.getDeleted() == null || ug.getDeleted() == 0)
                .map(BasicUserGroup::getUserId)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicUser>) userRepository.findAllById(userIds);
    }

    /** 全量覆盖用户组的用户关联 */
    public void setUsers(Long groupId, List<Long> userIds) {
        if (!repository.existsById(groupId)) {
            throw new IllegalArgumentException("用户组不存在: " + groupId);
        }
        userGroupRepository.findByGroupId(groupId).forEach(userGroupRepository::delete);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long userId : userIds) {
            if (userId == null) continue;
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

    // ---------- 用户组 - 用户组关联（basic_group_group，组层级） ----------

    /** 查询某个用户组的直属下级组 */
    public List<BasicGroup> getSubGroups(Long groupId) {
        List<Long> ids = groupGroupRepository.findByParentGroupId(groupId).stream()
                .filter(gg -> gg.getDeleted() == null || gg.getDeleted() == 0)
                .map(BasicGroupGroup::getGroupId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicGroup>) repository.findAllById(ids);
    }

    /** 全量覆盖某个用户组的下级组 */
    public void setSubGroups(Long groupId, List<Long> subIds) {
        if (!repository.existsById(groupId)) {
            throw new IllegalArgumentException("用户组不存在: " + groupId);
        }
        if (subIds != null && subIds.contains(groupId)) {
            throw new IllegalArgumentException("不能把用户组设为自己的下级");
        }
        groupGroupRepository.findByParentGroupId(groupId).forEach(groupGroupRepository::delete);
        saveGroupLinks(groupId, subIds);
    }

    /** 查询某个用户组的直属上级组 */
    public List<BasicGroup> getParentGroups(Long groupId) {
        List<Long> ids = groupGroupRepository.findByGroupId(groupId).stream()
                .filter(gg -> gg.getDeleted() == null || gg.getDeleted() == 0)
                .map(BasicGroupGroup::getParentGroupId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicGroup>) repository.findAllById(ids);
    }

    /** 全量覆盖某个用户组的上级组 */
    public void setParentGroups(Long groupId, List<Long> parentIds) {
        if (!repository.existsById(groupId)) {
            throw new IllegalArgumentException("用户组不存在: " + groupId);
        }
        if (parentIds != null && parentIds.contains(groupId)) {
            throw new IllegalArgumentException("不能把用户组设为自己的上级");
        }
        groupGroupRepository.findByGroupId(groupId).forEach(groupGroupRepository::delete);
        if (parentIds == null || parentIds.isEmpty()) {
            return;
        }
        for (Long parentId : parentIds) {
            if (parentId == null) continue;
            saveGroupLinks(parentId, List.of(groupId));
        }
    }

    /** 建立 parentId -> childIds 的上下级关联 */
    private void saveGroupLinks(Long parentId, List<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long opUser = RequestContext.currentUserId();
        for (Long childId : childIds) {
            if (childId == null) continue;
            BasicGroupGroup gg = new BasicGroupGroup();
            gg.setId(null);
            gg.setParentGroupId(parentId);
            gg.setGroupId(childId);
            gg.setDeleted(0);
            gg.setOpUserId(opUser);
            gg.setCreateTime(now);
            gg.setUpdateTime(now);
            groupGroupRepository.save(gg);
        }
    }
}
