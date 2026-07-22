package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.Permission;
import xyz.wewin.autumn.gateway.dashboard.service.PermissionService;

import java.util.List;

@RestController
@RequestMapping("/api/system/permission")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result<List<Permission>> list(@RequestParam(defaultValue = "1") int current,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) Long appId,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String name,
                                         @RequestParam(required = false) String permType) {
        Page<Permission> page = permissionService.list(current, pageSize, appId, code, name, permType);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Result<Permission> getById(@PathVariable Long id) {
        return permissionService.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "权限不存在"));
    }

    @PostMapping
    public Result<Permission> create(@RequestBody Permission permission) {
        try {
            return Result.success(permissionService.create(permission));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Permission> update(@PathVariable Long id, @RequestBody Permission permission) {
        try {
            return Result.success(permissionService.update(id, permission));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        permissionService.disable(id);
        return Result.success(null);
    }

    @PutMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        permissionService.enable(id);
        return Result.success(null);
    }
}