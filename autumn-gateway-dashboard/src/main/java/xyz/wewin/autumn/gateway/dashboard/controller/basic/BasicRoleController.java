package xyz.wewin.autumn.gateway.dashboard.controller.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicRoleService;

import java.util.List;

/**
 * 角色管理（basic_role），替代旧的 /api/system/role。
 */
@RestController
@RequestMapping("/api/system/basic/role")
public class BasicRoleController {

    @Autowired
    private BasicRoleService service;

    /** 分页列表：?current=1&pageSize=10&appId=&code=&name= */
    @GetMapping
    public Result<List<BasicRole>> list(@RequestParam(defaultValue = "1") int current,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) Long appId,
                                        @RequestParam(required = false) String code,
                                        @RequestParam(required = false) String name) {
        Page<BasicRole> page = service.list(appId, code, name, current, pageSize);
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

    /** 启用：status = 1 */
    @PutMapping("/{id}/enable")
    public Result<BasicRole> enable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 1));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 停用：status = 2 */
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
}
