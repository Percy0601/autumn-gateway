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
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenu;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicResource;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicResourceService;

import java.util.List;

/**
 * 资源管理（basic_resource），即权限原子项。
 *
 * <h3>管控策略</h3>
 * <ul>
 *   <li>查询、新增、编辑——<b>认证即可</b>；</li>
 *   <li>启停、以及「资源 ↔ 菜单」的<b>授权关联</b>——权限码管控。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/system/basic/resource")
public class BasicResourceController {

    @Autowired
    private BasicResourceService service;

    /** 分页列表：?current=1&pageSize=10&appId=&name=&httpMethod= */
    @GetMapping
    public Result<List<BasicResource>> list(@RequestParam(defaultValue = "1") int current,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Long appId,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) String httpMethod,
                                            @RequestParam(required = false) List<Long> boundIds) {
        Page<BasicResource> page = service.list(appId, name, httpMethod, boundIds, current, pageSize);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Result<BasicResource> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "资源不存在"));
    }

    @PostMapping
    public Result<BasicResource> create(@RequestBody BasicResource resource) {
        try {
            return Result.success(service.create(resource));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<BasicResource> update(@PathVariable Long id, @RequestBody BasicResource resource) {
        try {
            return Result.success(service.update(id, resource));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 启用：status = 1；需 {@code system:resource:enable} */
    @PreAuthorize("hasAuthority('system:resource:enable')")
    @PutMapping("/{id}/enable")
    public Result<BasicResource> enable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 1));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 禁用：status = 0；需 {@code system:resource:disable} */
    @PreAuthorize("hasAuthority('system:resource:disable')")
    @PutMapping("/{id}/disable")
    public Result<BasicResource> disable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 0));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<BasicResource>> listAll() {
        return Result.success(service.listAll());
    }

    // ---------- 关联维护 ----------

    /** 资源被哪些菜单关联（basic_menu_resource） */
    @GetMapping("/{id}/menus")
    public Result<List<BasicMenu>> menus(@PathVariable Long id) {
        return Result.success(service.getMenus(id));
    }

    /** 资源关联菜单：授权类操作，需 {@code system:resource:assign-menu} */
    @PreAuthorize("hasAuthority('system:resource:assign-menu')")
    @PutMapping("/{id}/menus")
    public Result<Void> setMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        try {
            service.setMenus(id, menuIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 反查：哪些角色授权了这个资源（只读） */
    @GetMapping("/{id}/roles")
    public Result<List<BasicRole>> roles(@PathVariable Long id) {
        return Result.success(service.getRoles(id));
    }
}
