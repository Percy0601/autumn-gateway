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
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicResource;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicResourceService;

import java.util.List;

/**
 * 资源管理（basic_resource），即权限原子项。
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
                                            @RequestParam(required = false) String httpMethod) {
        Page<BasicResource> page = service.list(appId, name, httpMethod, current, pageSize);
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

    /** 启用：status = 1 */
    @PutMapping("/{id}/enable")
    public Result<BasicResource> enable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 1));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 禁用：status = 0 */
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
}
