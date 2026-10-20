package xyz.wewin.autumn.gateway.dashboard.controller.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicGroup;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenu;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicResource;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicRoleService;

import java.util.List;

/**
 * 角色管理（basic_role），替代旧的 /api/system/role。
 *
 * <h3>管控策略</h3>
 * <ul>
 *   <li>查询、新增、编辑——<b>认证即可</b>；</li>
 *   <li>启停、以及「角色 → 用户 / 菜单 / 资源 / 用户组」四类<b>授权</b>操作——权限码管控。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/system/basic/role")
public class BasicRoleController {

    @Autowired
    private BasicRoleService service;

    /** 分页列表：?current=1&pageSize=10&namespaceId=&code=&name= */
    @GetMapping
    public Result<List<BasicRole>> list(@RequestParam(defaultValue = "1") int current,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) Long namespaceId,
                                        @RequestParam(required = false) String code,
                                        @RequestParam(required = false) String name) {
        Page<BasicRole> page = service.list(namespaceId, code, name, current, pageSize);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Result<BasicRole> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "角色不存在"));
    }

    @PostMapping
    public Result<BasicRole> create(@RequestBody BasicRole role) {
        try {
            return Result.success(service.create(role));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<BasicRole> update(@PathVariable Long id, @RequestBody BasicRole role) {
        try {
            return Result.success(service.update(id, role));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 启用：status = 1；需 {@code system:role:enable} */
    @PreAuthorize("hasAuthority('system:role:enable')")
    @PutMapping("/{id}/enable")
    public Result<BasicRole> enable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 1));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 停用：status = 2；需 {@code system:role:disable} */
    @PreAuthorize("hasAuthority('system:role:disable')")
    @PutMapping("/{id}/disable")
    public Result<BasicRole> disable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 2));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<BasicRole>> listAll() {
        return Result.success(service.listAll());
    }

    // ---------- 关联维护 ----------

    /** 角色已关联的用户 */
    @GetMapping("/{id}/users")
    public Result<List<BasicUser>> users(@PathVariable Long id) {
        return Result.success(service.getUsers(id));
    }

    /** 给角色分配用户：授权类操作，需 {@code system:role:assign-user} */
    @PreAuthorize("hasAuthority('system:role:assign-user')")
    @PutMapping("/{id}/users")
    public Result<Void> setUsers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        try {
            service.setUsers(id, userIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 角色已授权的菜单（basic_role_permission.type=1） */
    @GetMapping("/{id}/menus")
    public Result<List<BasicMenu>> menus(@PathVariable Long id) {
        return Result.success(service.getMenus(id));
    }

    /** 给角色分配菜单：授权类操作，需 {@code system:role:assign-menu} */
    @PreAuthorize("hasAuthority('system:role:assign-menu')")
    @PutMapping("/{id}/menus")
    public Result<Void> setMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        try {
            service.setMenus(id, menuIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 角色已授权的资源（basic_role_permission.type=2） */
    @GetMapping("/{id}/resources")
    public Result<List<BasicResource>> resources(@PathVariable Long id) {
        return Result.success(service.getResources(id));
    }

    /** 给角色分配资源：授权类操作，需 {@code system:role:assign-resource} */
    @PreAuthorize("hasAuthority('system:role:assign-resource')")
    @PutMapping("/{id}/resources")
    public Result<Void> setResources(@PathVariable Long id, @RequestBody List<Long> resourceIds) {
        try {
            service.setResources(id, resourceIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 角色已关联的用户组（basic_role_group） */
    @GetMapping("/{id}/groups")
    public Result<List<BasicGroup>> groups(@PathVariable Long id) {
        return Result.success(service.getGroups(id));
    }

    /** 给角色分配用户组：授权类操作，需 {@code system:role:assign-group} */
    @PreAuthorize("hasAuthority('system:role:assign-group')")
    @PutMapping("/{id}/groups")
    public Result<Void> setGroups(@PathVariable Long id, @RequestBody List<Long> groupIds) {
        try {
            service.setGroups(id, groupIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
