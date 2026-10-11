package xyz.wewin.autumn.gateway.dashboard.controller.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicGroupService;

import java.util.List;

/**
 * 用户组管理（basic_group），并维护用户组与角色的关联（basic_role_group）。
 */
@RestController
@RequestMapping("/api/system/basic/group")
public class BasicGroupController {

    @Autowired
    private BasicGroupService service;

    /** 分页列表：?current=1&pageSize=10&name=&code= */
    @GetMapping
    public Result<List<BasicGroup>> list(@RequestParam(defaultValue = "1") int current,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String name,
                                         @RequestParam(required = false) String code) {
        Page<BasicGroup> page = service.list(name, code, current, pageSize);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Result<BasicGroup> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "用户组不存在"));
    }

    @PostMapping
    public Result<BasicGroup> create(@RequestBody BasicGroup group) {
        try {
            return Result.success(service.create(group));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<BasicGroup> update(@PathVariable Long id, @RequestBody BasicGroup group) {
        try {
            return Result.success(service.update(id, group));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 软删除 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        try {
            service.remove(id);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<BasicGroup>> listAll() {
        return Result.success(service.listAll());
    }

    /** 用户组已关联的角色 */
    @GetMapping("/{id}/roles")
    public Result<List<BasicRole>> roles(@PathVariable Long id) {
        return Result.success(service.getRoles(id));
    }

    /** 全量覆盖关联：body 为角色 id 数组 */
    @PutMapping("/{id}/roles")
    public Result<Void> setRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        try {
            service.setRoles(id, roleIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
