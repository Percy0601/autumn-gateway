package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.Permission;
import xyz.wewin.autumn.gateway.dashboard.entity.Role;
import xyz.wewin.autumn.gateway.dashboard.entity.User;
import xyz.wewin.autumn.gateway.dashboard.service.RoleService;

import java.util.List;

@RestController
@RequestMapping("/api/system/role")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping
    public Result<List<Role>> list(@RequestParam(defaultValue = "1") int current,
                                   @RequestParam(defaultValue = "10") int pageSize,
                                   @RequestParam(required = false) Long appId,
                                   @RequestParam(required = false) String code,
                                   @RequestParam(required = false) String name) {
        Page<Role> page = roleService.list(current, pageSize, appId, code, name);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Result<Role> getById(@PathVariable Long id) {
        return roleService.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "角色不存在"));
    }

    @PostMapping
    public Result<Role> create(@RequestBody Role role) {
        try {
            return Result.success(roleService.create(role));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Role> update(@PathVariable Long id, @RequestBody Role role) {
        try {
            return Result.success(roleService.update(id, role));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        roleService.disable(id);
        return Result.success(null);
    }

    @PutMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        roleService.enable(id);
        return Result.success(null);
    }

    @GetMapping("/{id}/permissions")
    public Result<List<Permission>> getRolePermissions(@PathVariable Long id) {
        return Result.success(roleService.getRolePermissionIds(id));
    }

    @PutMapping("/{id}/permissions")
    public Result<Void> setRolePermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        roleService.setRolePermissions(id, permissionIds);
        return Result.success(null);
    }


    @GetMapping("/{id}/users")
    public Result<List<User>> getRoleUsers(@PathVariable Long id) {
        return Result.success(roleService.getRoleUsers(id));
    }

    @PutMapping("/{id}/users")
    public Result<Void> setRoleUsers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        roleService.setRoleUsers(id, userIds);
        return Result.success(null);
    }

    @GetMapping("/list")
    public Result<List<Role>> listAll() {
        return Result.success(roleService.listAll());
    }
}
