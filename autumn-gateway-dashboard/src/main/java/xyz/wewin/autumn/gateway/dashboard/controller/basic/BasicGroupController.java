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
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
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

    /** 分页列表：?current=1&pageSize=10&namespaceId=&name=&code= */
    @GetMapping
    public Result<List<BasicGroup>> list(@RequestParam(defaultValue = "1") int current,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) Long namespaceId,
                                         @RequestParam(required = false) String name,
                                         @RequestParam(required = false) String code) {
        Page<BasicGroup> page = service.list(namespaceId, name, code, current, pageSize);
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

    /** 用户组已关联的用户（组视角，与 /user/{id}/groups 互为反向） */
    @GetMapping("/{id}/users")
    public Result<List<BasicUser>> users(@PathVariable Long id) {
        return Result.success(service.getUsers(id));
    }

    /** 全量覆盖关联：body 为用户 id 数组 */
    @PutMapping("/{id}/users")
    public Result<Void> setUsers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        try {
            service.setUsers(id, userIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 用户组的直属下级组（basic_group_group） */
    @GetMapping("/{id}/sub-groups")
    public Result<List<BasicGroup>> subGroups(@PathVariable Long id) {
        return Result.success(service.getSubGroups(id));
    }

    /** 全量覆盖下级组：body 为用户组 id 数组 */
    @PutMapping("/{id}/sub-groups")
    public Result<Void> setSubGroups(@PathVariable Long id, @RequestBody List<Long> subIds) {
        try {
            service.setSubGroups(id, subIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 用户组的直属上级组（basic_group_group） */
    @GetMapping("/{id}/parent-groups")
    public Result<List<BasicGroup>> parentGroups(@PathVariable Long id) {
        return Result.success(service.getParentGroups(id));
    }

    /** 全量覆盖上级组：body 为用户组 id 数组 */
    @PutMapping("/{id}/parent-groups")
    public Result<Void> setParentGroups(@PathVariable Long id, @RequestBody List<Long> parentIds) {
        try {
            service.setParentGroups(id, parentIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 启用：status = 1 */
    @PutMapping("/{id}/enable")
    public Result<BasicGroup> enable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 1));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 停用：status = 0 */
    @PutMapping("/{id}/disable")
    public Result<BasicGroup> disable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 0));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
