package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.*;
import xyz.wewin.autumn.gateway.dashboard.repo.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleService {

    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private UserRepository userRepository;
    public Page<Role> list(int page, int size, Long appId, String code, String name) {
        List<Role> content = roleRepository.findWithPage(appId,
                code,
                name,
                PageRequest.of(page - 1, size));

        long total = roleRepository.countWithFilter(appId,
                code,
                name);
        return new PageImpl<>(content, PageRequest.of(page - 1, size), total);
    }

    public Optional<Role> getById(Long id) {
        return roleRepository.findById(id);
    }

    public Role create(Role role) {
        if (roleRepository.findByAppIdAndCode(role.getAppId(), role.getCode()).isPresent()) {
            throw new IllegalArgumentException("该应用下角色编码已存在");
        }
        role.setId(null);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        return roleRepository.save(role);
    }

    public Role update(Long id, Role req) {
        Role existing = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        // 如果修改了 appId 或 code，需检查唯一性
        if ((!existing.getAppId().equals(req.getAppId()) || !existing.getCode().equals(req.getCode()))
                && roleRepository.findByAppIdAndCode(req.getAppId(), req.getCode()).isPresent()) {
            throw new IllegalArgumentException("该应用下角色编码已存在");
        }
        if (req.getAppId() != null) existing.setAppId(req.getAppId());
        if (req.getCode() != null) existing.setCode(req.getCode());
        if (req.getName() != null) existing.setName(req.getName());


        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return roleRepository.save(existing);
    }

    public void disable(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        role.setStatus(0);
        role.setUpdatedAt(LocalDateTime.now());
        roleRepository.save(role);
    }

    public void enable(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        role.setStatus(1);
        role.setUpdatedAt(LocalDateTime.now());
        roleRepository.save(role);
    }

    /**
     * 获取角色的权限ID列表
     */
    public List<Permission> getRolePermissionIds(Long roleId) {
        List<Long> permissionIds = rolePermissionRepository.findByRoleId(roleId)
                .stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toList());

        List<Permission> permissions = new ArrayList<>();
        permissionRepository.findAllById(permissionIds).forEach(it -> {
            permissions.add(it);
        });
        return permissions;
    }

    /**
     * 设置角色的权限（全量覆盖）
     */
    public void setRolePermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionRepository.deleteByRoleId(roleId);
        permissionIds.forEach(pid -> rolePermissionRepository.insert(roleId, pid));
    }

    public List<Role> listAll() {
        List<Role> roles = new ArrayList<>();
        roleRepository.findAll().forEach(roles::add);
        return roles;
    }

    public List<User> getRoleUsers(Long id) {
        List<Long> userIds = userRoleRepository.findByRoleId(id)
                .stream()
                .map(UserRole::getUserId)
                .collect(Collectors.toList());
        List<User> users = new ArrayList<>();
        userRepository.findAllById(userIds).forEach(it -> {
            users.add(it);
        });
        return users;
    }

    public void setRoleUsers(Long roleId, List<Long> userIds) {
        userRoleRepository.deleteByRoleId(roleId);
        Date now = new Date();
        Long appId = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"))
                .getAppId();
        userIds.forEach(uid -> {
            userRoleRepository.insert(uid, roleId, appId, now.getTime());
        });

    }
}