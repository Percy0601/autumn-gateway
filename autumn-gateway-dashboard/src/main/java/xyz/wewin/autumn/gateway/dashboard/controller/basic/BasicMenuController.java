package xyz.wewin.autumn.gateway.dashboard.controller.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenu;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicResource;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.security.LoginUser;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicMenuService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单管理（basic_menu）。
 * 前端按 parentId 自行构建树，这里返回扁平列表。
 *
 * <h3>管控策略</h3>
 * <ul>
 *   <li>查询、新增、编辑——<b>认证即可</b>；</li>
 *   <li>启停、以及「菜单 ↔ 资源」的<b>授权关联</b>——权限码管控。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/system/basic/menu")
public class BasicMenuController {

    private static final Logger log = LoggerFactory.getLogger(BasicMenuController.class);

    @Autowired
    private BasicMenuService service;

    /** 分页列表：?current=1&pageSize=9999&appId= */
    @GetMapping
    public Result<List<BasicMenu>> list(@RequestParam(defaultValue = "1") int current,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) Long appId,
                                        @RequestParam(required = false) String menuName,
                                        @RequestParam(required = false) List<Long> boundIds) {
        Page<BasicMenu> page = service.list(appId, menuName, boundIds, current, pageSize);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    /**
     * 按应用返回菜单列表。
     *
     * <p><b>v6 起按权限过滤</b>：这是「不同角色看到不同目录」的落地接口。
     * 返回规则：
     * <ul>
     *   <li>超管 / {@code basic.rbac.enabled=false} → 返回全量（保持既有行为）；</li>
     *   <li>普通用户 → 只返回其被授权的菜单，且自动补齐祖先目录，
     *       避免子菜单有权限但父目录缺失导致前端渲染不出层级。</li>
     * </ul>
     *
     * <p>前端 {@code routerStore.getMenuRoutes()} 直接用这个结果建路由表，
     * 因此无需改动前端即可实现目录级隔离。</p>
     */
    @GetMapping("/list")
    public Result<List<BasicMenu>> listByApp(@RequestParam(required = false) Long appId) {
        List<BasicMenu> all = service.listByApp(appId);

        LoginUser current = currentUser();
        if (current == null || current.isSuperAdmin() || current.permissions().contains("*")) {
            return Result.success(all);
        }

        Set<String> granted = new HashSet<>(current.permissions());
        if (granted.isEmpty()) {
            // 无任何授权：返回空列表，前端会走 fallbackMenu——这并不安全，
            // 所以同时把情况记下，便于排查「登录后看不到菜单」的工单
            log.warn("用户 {} 没有任何菜单授权，返回空菜单", current.getUserId());
            return Result.success(List.of());
        }

        // 1) 先筛出直接有权限的菜单
        Map<Long, BasicMenu> visible = new LinkedHashMap<>();
        for (BasicMenu m : all) {
            if (m.getMenuIdentification() != null && granted.contains(m.getMenuIdentification())) {
                visible.put(m.getId(), m);
            }
        }
        // 2) 补齐祖先：父目录没有独立权限点时也必须能显示，否则前端建不出树
        Map<Long, BasicMenu> byId = new HashMap<>();
        for (BasicMenu m : all) {
            byId.put(m.getId(), m);
        }
        Set<Long> toAdd = new HashSet<>(visible.keySet());
        for (Long id : toAdd) {
            BasicMenu m = byId.get(id);
            while (m != null && m.getParentId() != null && m.getParentId() != 0L) {
                BasicMenu parent = byId.get(m.getParentId());
                if (parent == null || visible.containsKey(parent.getId())) {
                    break;
                }
                visible.put(parent.getId(), parent);
                m = parent;
            }
        }
        return Result.success(new ArrayList<>(visible.values()));
    }

    @GetMapping("/{id}")
    public Result<BasicMenu> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "菜单不存在"));
    }

    @PostMapping
    public Result<BasicMenu> create(@RequestBody BasicMenu menu) {
        try {
            return Result.success(service.create(menu));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<BasicMenu> update(@PathVariable Long id, @RequestBody BasicMenu menu) {
        try {
            return Result.success(service.update(id, menu));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 启用：menu_status = 1；需 {@code system:menu:enable} */
    @PreAuthorize("hasAuthority('system:menu:enable')")
    @PutMapping("/{id}/enable")
    public Result<BasicMenu> enable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 1));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 停用：menu_status = 2；需 {@code system:menu:disable} */
    @PreAuthorize("hasAuthority('system:menu:disable')")
    @PutMapping("/{id}/disable")
    public Result<BasicMenu> disable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 2));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    // ---------- 关联维护 ----------

    /** 菜单已关联的资源（basic_menu_resource） */
    @GetMapping("/{id}/resources")
    public Result<List<BasicResource>> resources(@PathVariable Long id) {
        return Result.success(service.getResources(id));
    }

    /** 菜单关联资源：授权类操作，需 {@code system:menu:assign-resource} */
    @PreAuthorize("hasAuthority('system:menu:assign-resource')")
    @PutMapping("/{id}/resources")
    public Result<Void> setResources(@PathVariable Long id, @RequestBody List<Long> resourceIds) {
        try {
            service.setResources(id, resourceIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 反查：哪些角色授权了这个菜单（只读） */
    @GetMapping("/{id}/roles")
    public Result<List<BasicRole>> roles(@PathVariable Long id) {
        return Result.success(service.getRoles(id));
    }

    /**
     * 取当前登录主体。鉴权未启用时 SecurityContext 为空，返回 null，
     * 调用方据此退化为「返回全量」，保证灰度期行为与改造前一致。
     */
    private LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof LoginUser user ? user : null;
    }
}
