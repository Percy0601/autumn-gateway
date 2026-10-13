package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicApp;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenu;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenuResource;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicResource;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRolePermission;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicAppRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicMenuRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicMenuResourceRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicResourceRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRolePermissionRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRoleRepository;
import xyz.wewin.autumn.gateway.dashboard.security.RequestContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 资源（权限原子项）服务，基于 basic_resource。
 */
@Service
@Transactional
public class BasicResourceService {

    @Autowired
    private BasicResourceRepository repository;

    @Autowired
    private BasicAppRepository appRepository;

    @Autowired
    private BasicMenuResourceRepository menuResourceRepository;

    @Autowired
    private BasicMenuRepository menuRepository;

    @Autowired
    private BasicRolePermissionRepository rolePermissionRepository;

    @Autowired
    private BasicRoleRepository roleRepository;

    /** basic_role_permission.type：1-菜单 2-资源 */
    private static final int PERM_TYPE_RESOURCE = 2;

    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    /** 空集合用 {-1} 占位，避免 SQL 出现 IN () */
    private static List<Long> normalizeBoundIds(List<Long> boundIds) {
        return (boundIds == null || boundIds.isEmpty()) ? List.of(-1L) : boundIds;
    }

    public Page<BasicResource> list(Long appId, String name, String httpMethod,
                                    List<Long> boundIds, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<Long> bound = normalizeBoundIds(boundIds);
        List<BasicResource> content =
                repository.findWithPage(appId, nvl(name), nvl(httpMethod), bound, offset, (long) size);
        long total = repository.countWithFilter(appId, nvl(name), nvl(httpMethod), bound);
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<BasicResource> getById(Long id) {
        return repository.findById(id);
    }

    public BasicResource create(BasicResource res) {
        if (res.getAppId() == null) {
            throw new IllegalArgumentException("所属应用不能为空");
        }
        if (res.getName() == null || res.getName().isBlank()) {
            throw new IllegalArgumentException("资源名称不能为空");
        }
        res.setId(null);
        // 冗余一份 app_code，便于按应用编码检索
        if (res.getAppCode() == null || res.getAppCode().isBlank()) {
            res.setAppCode(appRepository.findById(res.getAppId()).map(BasicApp::getAppCode).orElse(""));
        }
        if (res.getHttpMethod() == null) res.setHttpMethod("ALL");
        if (res.getMatchType() == null) res.setMatchType("exact");
        if (res.getStatus() == null) res.setStatus(1);
        res.setCreatedAt(LocalDateTime.now());
        res.setUpdatedAt(LocalDateTime.now());
        return repository.save(res);
    }

    public BasicResource update(Long id, BasicResource req) {
        BasicResource existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资源不存在: " + id));
        if (req.getAppId() != null) existing.setAppId(req.getAppId());
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getPath() != null) existing.setPath(req.getPath());
        if (req.getHttpMethod() != null) existing.setHttpMethod(req.getHttpMethod());
        if (req.getMatchType() != null) existing.setMatchType(req.getMatchType());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (existing.getAppId() != null) {
            existing.setAppCode(appRepository.findById(existing.getAppId()).map(BasicApp::getAppCode).orElse(""));
        }
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    /** status: 1=正常 0=禁用 */
    public BasicResource updateStatus(Long id, Integer status) {
        BasicResource existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资源不存在: " + id));
        existing.setStatus(status);
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    public List<BasicResource> listAll() {
        return (List<BasicResource>) repository.findAll();
    }

    // ---------- 资源 - 菜单关联（basic_menu_resource） ----------

    /** 资源被哪些菜单关联 */
    public List<BasicMenu> getMenus(Long resourceId) {
        List<Long> ids = menuResourceRepository.findByResourceId(resourceId).stream()
                .filter(mr -> mr.getDeleted() == null || mr.getDeleted() == 0)
                .map(BasicMenuResource::getMenuId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicMenu>) menuRepository.findAllById(ids);
    }

    /** 全量覆盖资源的菜单关联 */
    public void setMenus(Long resourceId, List<Long> menuIds) {
        if (!repository.existsById(resourceId)) {
            throw new IllegalArgumentException("资源不存在: " + resourceId);
        }
        menuResourceRepository.findByResourceId(resourceId).forEach(menuResourceRepository::delete);
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long opUser = RequestContext.currentUserId();
        for (Long menuId : menuIds) {
            if (menuId == null) continue;
            BasicMenuResource mr = new BasicMenuResource();
            mr.setId(null);
            mr.setMenuId(menuId);
            mr.setResourceId(resourceId);
            mr.setDeleted(0);
            mr.setOpUserId(opUser);
            mr.setCreateTime(now);
            mr.setUpdateTime(now);
            menuResourceRepository.save(mr);
        }
    }

    // ---------- 资源 - 角色（反查，只读） ----------

    /** 哪些角色授权了这个资源（basic_role_permission.type=2 反查） */
    public List<BasicRole> getRoles(Long resourceId) {
        List<Long> ids = rolePermissionRepository.findByRefIdAndType(resourceId, PERM_TYPE_RESOURCE).stream()
                .filter(p -> p.getDelFlag() == null || p.getDelFlag() == 0)
                .map(BasicRolePermission::getRoleId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicRole>) roleRepository.findAllById(ids);
    }
}
