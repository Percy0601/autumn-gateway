package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicGroup;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenu;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicNamespace;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicResource;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRoleGroup;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRolePermission;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUserRole;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicMenuRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicNamespaceRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicResourceRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRoleGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRolePermissionRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRoleRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRoleRepository;
import xyz.wewin.autumn.gateway.dashboard.security.RequestContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import xyz.wewin.autumn.gateway.dashboard.security.GrantChanged;

/**
 * 角色服务，基于 basic_role。
 *
 * <p>角色可跨应用，归属命名空间（basic_namespace）。同一命名空间内，角色编码与角色名称均不可重复。
 *
 * <p>basic_role 的 create_user / update_user 为 NOT NULL，
 * 这里统一从 {@link RequestContext}（由 JWT 解析得到）取当前操作人。
 */
@Service
@Transactional
public class BasicRoleService {

    @Autowired
    private BasicRoleRepository repository;

    @Autowired
    private BasicRoleGroupRepository roleGroupRepository;

    @Autowired
    private BasicGroupRepository groupRepository;

    @Autowired
    private BasicUserRoleRepository userRoleRepository;

    @Autowired
    private BasicUserRepository userRepository;

    @Autowired
    private BasicRolePermissionRepository rolePermissionRepository;

    @Autowired
    private BasicMenuRepository menuRepository;

    @Autowired
    private BasicResourceRepository resourceRepository;

    @Autowired
    private BasicNamespaceRepository namespaceRepository;

    /** basic_role_permission.type：1-菜单 2-资源 3-数据权限 */
    private static final int PERM_TYPE_MENU = 1;
    private static final int PERM_TYPE_RESOURCE = 2;

    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    public Page<BasicRole> list(Long namespaceId, String code, String name, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<BasicRole> content = repository.findWithPage(namespaceId, nvl(code), nvl(name), offset, (long) size);
        long total = repository.countWithFilter(namespaceId, nvl(code), nvl(name));
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<BasicRole> getById(Long id) {
        return repository.findById(id);
    }

    public BasicRole create(BasicRole role) {
        if (role.getCode() == null || role.getCode().isBlank()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        if (role.getNamespaceId() == null) {
            throw new IllegalArgumentException("所属命名空间不能为空");
        }
        if (role.getName() == null || role.getName().isBlank()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        BasicNamespace ns = namespaceRepository.findById(role.getNamespaceId())
                .orElseThrow(() -> new IllegalArgumentException("命名空间不存在: " + role.getNamespaceId()));
        // 同一命名空间下，角色编码与角色名称均不允许重复
        if (repository.countByNamespaceIdAndCode(role.getNamespaceId(), role.getCode()) > 0) {
            throw new IllegalArgumentException("角色编码已存在（命名空间 " + role.getNamespaceId() + "）: " + role.getCode());
        }
        if (repository.countByNamespaceIdAndName(role.getNamespaceId(), role.getName()) > 0) {
            throw new IllegalArgumentException("角色名称已存在（命名空间 " + role.getNamespaceId() + "）: " + role.getName());
        }
        role.setId(null);
        role.setNamespaceCode(ns.getCode());
        // name / remark 在库里是 NOT NULL DEFAULT ''；前端用 `|| undefined` 省略空字段时
        // 这里是 null，Spring Data JDBC 会插入显式 NULL 触发约束冲突，必须兜底
        if (role.getRemark() == null) role.setRemark("");
        if (role.getStatus() == null) role.setStatus(1);
        if (role.getType() == null) role.setType(2);
        if (role.getMaster() == null) role.setMaster(2);
        if (role.getDelFlag() == null) role.setDelFlag(0);
        role.setCreateUser(RequestContext.currentUserId());
        role.setUpdateUser(RequestContext.currentUserId());
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        return repository.save(role);
    }

    public BasicRole update(Long id, BasicRole req) {
        BasicRole existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));
        // 命名空间可能随更新改变，唯一性判定统一基于“生效后的命名空间 ID”
        Long effectiveNsId = req.getNamespaceId() != null ? req.getNamespaceId() : existing.getNamespaceId();
        if (req.getNamespaceId() != null) {
            BasicNamespace ns = namespaceRepository.findById(req.getNamespaceId())
                    .orElseThrow(() -> new IllegalArgumentException("命名空间不存在: " + req.getNamespaceId()));
            existing.setNamespaceId(req.getNamespaceId());
            existing.setNamespaceCode(ns.getCode());
        }
        if (req.getCode() != null) {
            if (!req.getCode().equals(existing.getCode())
                    && repository.countByIdNotAndNamespaceIdAndCode(id, effectiveNsId, req.getCode()) > 0) {
                throw new IllegalArgumentException("角色编码已存在（命名空间 " + effectiveNsId + "）: " + req.getCode());
            }
            existing.setCode(req.getCode());
        }
        if (req.getName() != null) {
            if (!req.getName().equals(existing.getName())
                    && repository.countByIdNotAndNamespaceIdAndName(id, effectiveNsId, req.getName()) > 0) {
                throw new IllegalArgumentException("角色名称已存在（命名空间 " + effectiveNsId + "）: " + req.getName());
            }
            existing.setName(req.getName());
        }
        if (req.getType() != null) existing.setType(req.getType());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getMaster() != null) existing.setMaster(req.getMaster());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        existing.setUpdateUser(RequestContext.currentUserId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    /** status: 1=启用 2=停用 */
    public BasicRole updateStatus(Long id, Integer status) {
        BasicRole existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));
        existing.setStatus(status);
        existing.setUpdateUser(RequestContext.currentUserId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    public List<BasicRole> listAll() {
        return (List<BasicRole>) repository.findAll();
    }

    // ---------- 角色 - 用户关联（basic_user_role） ----------

    /** 查询角色已关联的用户 */
    public List<BasicUser> getUsers(Long roleId) {
        List<Long> userIds = userRoleRepository.findByRoleId(roleId).stream()
                .filter(ur -> ur.getDelFlag() == null || ur.getDelFlag() == 0)
                .map(BasicUserRole::getUserId)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicUser>) userRepository.findAllById(userIds);
    }

    /** 全量覆盖角色的用户关联 */
    @GrantChanged
    public void setUsers(Long roleId, List<Long> userIds) {
        if (!repository.existsById(roleId)) {
            throw new IllegalArgumentException("角色不存在: " + roleId);
        }
        userRoleRepository.findByRoleId(roleId).forEach(userRoleRepository::delete);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long opUser = RequestContext.currentUserId();
        for (Long userId : userIds) {
            if (userId == null) continue;
            BasicUserRole ur = new BasicUserRole();
            ur.setId(null);
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            // 角色已跨应用，用户-角色关联不再绑定单一应用，统一置 0（命名空间级）
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

    // ---------- 角色 - 菜单 / 资源关联（basic_role_permission） ----------

    /** 查询角色已授权的菜单（type=1） */
    public List<BasicMenu> getMenus(Long roleId) {
        List<Long> ids = permissionRefIds(roleId, PERM_TYPE_MENU);
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicMenu>) menuRepository.findAllById(ids);
    }

    /** 全量覆盖角色的菜单授权（type=1） */
    @GrantChanged
    public void setMenus(Long roleId, List<Long> menuIds) {
        setPermissions(roleId, menuIds, PERM_TYPE_MENU);
    }

    /** 查询角色已授权的资源（type=2） */
    public List<BasicResource> getResources(Long roleId) {
        List<Long> ids = permissionRefIds(roleId, PERM_TYPE_RESOURCE);
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicResource>) resourceRepository.findAllById(ids);
    }

    /** 全量覆盖角色的资源授权（type=2） */
    @GrantChanged
    public void setResources(Long roleId, List<Long> resourceIds) {
        setPermissions(roleId, resourceIds, PERM_TYPE_RESOURCE);
    }

    /** 取某角色、某类权限已授权的 ref_id 列表 */
    private List<Long> permissionRefIds(Long roleId, int type) {
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .filter(p -> p.getDelFlag() == null || p.getDelFlag() == 0)
                .filter(p -> p.getType() != null && p.getType() == type)
                .map(BasicRolePermission::getRefId)
                .distinct()
                .toList();
    }

    /**
     * 全量覆盖某类权限关联。
     * 只清理同 type 的记录，避免把菜单/资源/数据权限互相误删。
     */
    private void setPermissions(Long roleId, List<Long> refIds, int type) {
        if (!repository.existsById(roleId)) {
            throw new IllegalArgumentException("角色不存在: " + roleId);
        }
        rolePermissionRepository.findByRoleId(roleId).stream()
                .filter(p -> p.getType() != null && p.getType() == type)
                .forEach(rolePermissionRepository::delete);
        if (refIds == null || refIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long opUser = RequestContext.currentUserId();
        for (Long refId : refIds) {
            if (refId == null) continue;
            BasicRolePermission p = new BasicRolePermission();
            p.setId(null);
            p.setRoleId(roleId);
            p.setRefId(refId);
            p.setType(type);
            p.setDelFlag(0);
            p.setOpUserId(opUser);
            p.setCreateTime(now);
            p.setUpdateTime(now);
            rolePermissionRepository.save(p);
        }
    }

    // ---------- 角色 - 用户组关联（basic_role_group） ----------

    /** 角色已关联的用户组 */
    public List<BasicGroup> getGroups(Long roleId) {
        List<Long> ids = roleGroupRepository.findByRoleId(roleId).stream()
                .filter(rg -> rg.getDeleted() == null || rg.getDeleted() == 0)
                .map(BasicRoleGroup::getGroupId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicGroup>) groupRepository.findAllById(ids);
    }

    /** 全量覆盖角色的用户组关联 */
    @GrantChanged
    public void setGroups(Long roleId, List<Long> groupIds) {
        if (!repository.existsById(roleId)) {
            throw new IllegalArgumentException("角色不存在: " + roleId);
        }
        roleGroupRepository.findByRoleId(roleId).forEach(roleGroupRepository::delete);
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long opUser = RequestContext.currentUserId();
        for (Long groupId : groupIds) {
            if (groupId == null) continue;
            BasicRoleGroup rg = new BasicRoleGroup();
            rg.setId(null);
            rg.setRoleId(roleId);
            rg.setGroupId(groupId);
            rg.setDeleted(0);
            rg.setOpUserId(opUser);
            rg.setCreateTime(now);
            rg.setUpdateTime(now);
            roleGroupRepository.save(rg);
        }
    }
}
