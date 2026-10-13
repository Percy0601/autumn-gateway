package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenu;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenuResource;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicResource;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRolePermission;
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
 * 菜单服务，基于 basic_menu。
 *
 * <p>basic_menu 的 create_user / update_user 同样为 NOT NULL，从 {@link RequestContext} 取值。
 */
@Service
@Transactional
public class BasicMenuService {

    @Autowired
    private BasicMenuRepository repository;

    @Autowired
    private BasicMenuResourceRepository menuResourceRepository;

    @Autowired
    private BasicResourceRepository resourceRepository;

    @Autowired
    private BasicRolePermissionRepository rolePermissionRepository;

    @Autowired
    private BasicRoleRepository roleRepository;

    /** basic_role_permission.type：1-菜单 2-资源 */
    private static final int PERM_TYPE_MENU = 1;

    public Page<BasicMenu> list(Long appId, String menuName, List<Long> boundIds,
                                int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        String nameFilter = (menuName == null || menuName.isBlank()) ? "" : menuName.trim();
        List<Long> bound = normalizeBoundIds(boundIds);
        List<BasicMenu> content =
                repository.findWithPage(appId, nameFilter, null, bound, offset, (long) size);
        long total = repository.countWithFilter(appId, nameFilter, null, bound);
        return new PageImpl<>(content, pageable, total);
    }

    /** 空集合用 {-1} 占位，避免 SQL 出现 IN () */
    private static List<Long> normalizeBoundIds(List<Long> boundIds) {
        return (boundIds == null || boundIds.isEmpty()) ? List.of(-1L) : boundIds;
    }

    /** 按应用返回全量菜单（前端据此构建树） */
    public List<BasicMenu> listByApp(Long appId) {
        return appId == null ? (List<BasicMenu>) repository.findAll() : repository.findByAppId(appId);
    }

    public Optional<BasicMenu> getById(Long id) {
        return repository.findById(id);
    }

    public BasicMenu create(BasicMenu menu) {
        if (menu.getMenuName() == null || menu.getMenuName().isBlank()) {
            throw new IllegalArgumentException("菜单名称不能为空");
        }
        menu.setId(null);
        // menu_url / menu_identification / menu_icon / remark 在库里都是 NOT NULL DEFAULT ''，
        // 前端省略空字段时为 null，必须兜底，否则插入会触发非空约束冲突
        if (menu.getMenuUrl() == null) menu.setMenuUrl("");
        if (menu.getMenuIdentification() == null) menu.setMenuIdentification("");
        if (menu.getMenuIcon() == null) menu.setMenuIcon("");
        if (menu.getRemark() == null) menu.setRemark("");
        if (menu.getParentId() == null) menu.setParentId(0L);
        if (menu.getMenuStatus() == null) menu.setMenuStatus(1);
        if (menu.getMenuType() == null) menu.setMenuType(2);
        if (menu.getSortNum() == null) menu.setSortNum(0);
        if (menu.getDelFlag() == null) menu.setDelFlag(0);
        menu.setCreateUser(RequestContext.currentUserId());
        menu.setUpdateUser(RequestContext.currentUserId());
        menu.setCreateTime(LocalDateTime.now());
        menu.setUpdateTime(LocalDateTime.now());
        return repository.save(menu);
    }

    public BasicMenu update(Long id, BasicMenu req) {
        BasicMenu existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜单不存在: " + id));
        if (req.getAppId() != null) existing.setAppId(req.getAppId());
        if (req.getParentId() != null) existing.setParentId(req.getParentId());
        if (req.getMenuType() != null) existing.setMenuType(req.getMenuType());
        if (req.getMenuName() != null) existing.setMenuName(req.getMenuName());
        if (req.getMenuUrl() != null) existing.setMenuUrl(req.getMenuUrl());
        if (req.getMenuIdentification() != null) existing.setMenuIdentification(req.getMenuIdentification());
        if (req.getMenuIcon() != null) existing.setMenuIcon(req.getMenuIcon());
        if (req.getSortNum() != null) existing.setSortNum(req.getSortNum());
        if (req.getMenuStatus() != null) existing.setMenuStatus(req.getMenuStatus());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        existing.setUpdateUser(RequestContext.currentUserId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    /** menuStatus: 1=启用 2=停用 */
    public BasicMenu updateStatus(Long id, Integer menuStatus) {
        BasicMenu existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜单不存在: " + id));
        existing.setMenuStatus(menuStatus);
        existing.setUpdateUser(RequestContext.currentUserId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    // ---------- 菜单 - 资源关联（basic_menu_resource） ----------

    /** 菜单已关联的资源 */
    public List<BasicResource> getResources(Long menuId) {
        List<Long> ids = menuResourceRepository.findByMenuId(menuId).stream()
                .filter(mr -> mr.getDeleted() == null || mr.getDeleted() == 0)
                .map(BasicMenuResource::getResourceId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicResource>) resourceRepository.findAllById(ids);
    }

    /** 全量覆盖菜单的资源关联 */
    public void setResources(Long menuId, List<Long> resourceIds) {
        if (!repository.existsById(menuId)) {
            throw new IllegalArgumentException("菜单不存在: " + menuId);
        }
        menuResourceRepository.findByMenuId(menuId).forEach(menuResourceRepository::delete);
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long opUser = RequestContext.currentUserId();
        for (Long resourceId : resourceIds) {
            if (resourceId == null) continue;
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

    // ---------- 菜单 - 角色（反查，只读） ----------

    /** 哪些角色授权了这个菜单（basic_role_permission.type=1 反查） */
    public List<BasicRole> getRoles(Long menuId) {
        List<Long> ids = rolePermissionRepository.findByRefIdAndType(menuId, PERM_TYPE_MENU).stream()
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
