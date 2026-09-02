package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.Permission;
import xyz.wewin.autumn.gateway.dashboard.repo.PermissionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PermissionService {

    @Autowired
    private PermissionRepository permissionRepository;

    public Page<Permission> list(int page, int size, Long appId, String code, String name, String permType) {

        long offset = (page - 1) * size;
        long limit = size;
        List<Permission> content = permissionRepository.findWithPage(appId,
                code,
                name,
                permType,
                offset,
                limit);
        long total = permissionRepository.countWithFilter(appId, code, name, permType);
        return new PageImpl<>(content, PageRequest.of(page - 1, size), total);
    }

    public Optional<Permission> getById(Long id) {
        return permissionRepository.findById(id);
    }

    public Permission create(Permission permission) {
        if (permissionRepository.findByAppIdAndCode(permission.getAppId(), permission.getCode()).isPresent()) {
            throw new IllegalArgumentException("该应用下权限编码已存在");
        }
        permission.setId(null);
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        return permissionRepository.save(permission);
    }

    public Permission update(Long id, Permission req) {
        Permission existing = permissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("权限不存在"));
        if ((!existing.getAppId().equals(req.getAppId()) || !existing.getCode().equals(req.getCode()))
                && permissionRepository.findByAppIdAndCode(req.getAppId(), req.getCode()).isPresent()) {
            throw new IllegalArgumentException("该应用下权限编码已存在");
        }
        if (req.getAppId() != null) existing.setAppId(req.getAppId());
        if (req.getCategory() != null) existing.setCategory(req.getCategory());
        if (req.getCode() != null) existing.setCode(req.getCode());
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getPermType() != null) existing.setPermType(req.getPermType());
        if (req.getResourcePath() != null) existing.setResourcePath(req.getResourcePath());
        if (req.getHttpMethod() != null) existing.setHttpMethod(req.getHttpMethod());
        if (req.getMatchType() != null) existing.setMatchType(req.getMatchType());
        if (req.getParentId() != null) existing.setParentId(req.getParentId());
        if (req.getIcon() != null) existing.setIcon(req.getIcon());
        if (req.getSort() != null) existing.setSort(req.getSort());
        if (req.getHidden() != null) existing.setHidden(req.getHidden());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return permissionRepository.save(existing);
    }

    public void disable(Long id) {
        Permission p = permissionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("权限不存在"));
        p.setStatus(0);
        p.setUpdatedAt(LocalDateTime.now());
        permissionRepository.save(p);
    }

    public void enable(Long id) {
        Permission p = permissionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("权限不存在"));
        p.setStatus(1);
        p.setUpdatedAt(LocalDateTime.now());
        permissionRepository.save(p);
    }

    public List<Permission> listAll() {
        List<Permission> permissions = new ArrayList<>();
        permissionRepository.findAll().forEach(permissions::add);
        return permissions;
    }
}
