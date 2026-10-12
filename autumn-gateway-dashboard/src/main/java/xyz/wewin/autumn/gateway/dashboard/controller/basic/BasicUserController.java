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
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicUserService;

import java.util.List;

/**
 * 用户管理（basic_user），替代旧的 /api/system/user。
 * 同时维护用户与用户组的关联（basic_user_group）。
 */
@RestController
@RequestMapping("/api/system/basic/user")
public class BasicUserController {

    @Autowired
    private BasicUserService service;

    /** 分页列表：?current=1&pageSize=10&empNo=&username=&phone=&status=（status 不传或 -1 表示不过滤） */
    @GetMapping
    public Result<List<BasicUser>> list(@RequestParam(defaultValue = "1") int current,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) String empNo,
                                        @RequestParam(required = false) String username,
                                        @RequestParam(required = false) String phone,
                                        @RequestParam(required = false) Integer status) {
        Page<BasicUser> page = service.list(empNo, username, phone, status, current, pageSize);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Result<BasicUser> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "用户不存在"));
    }

    @PostMapping
    public Result<BasicUser> create(@RequestBody BasicUser user) {
        try {
            return Result.success(service.create(user));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<BasicUser> update(@PathVariable Long id, @RequestBody BasicUser user) {
        try {
            return Result.success(service.update(id, user));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        try {
            service.remove(id);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 用户已关联的用户组 */
    @GetMapping("/{id}/groups")
    public Result<List<BasicGroup>> groups(@PathVariable Long id) {
        return Result.success(service.getGroups(id));
    }

    /** 全量覆盖关联：body 为用户组 id 数组 */
    @PutMapping("/{id}/groups")
    public Result<Void> setGroups(@PathVariable Long id, @RequestBody List<Long> groupIds) {
        try {
            service.setGroups(id, groupIds);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 用户已直接关联的角色 */
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
