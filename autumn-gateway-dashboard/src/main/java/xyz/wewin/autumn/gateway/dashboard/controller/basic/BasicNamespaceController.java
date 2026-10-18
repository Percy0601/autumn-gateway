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
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicNamespace;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicNamespaceService;

import java.util.List;

/**
 * 命名空间（租户）管理，对应 basic_namespace。
 */
@RestController
@RequestMapping("/api/system/basic/namespace")
public class BasicNamespaceController {

    @Autowired
    private BasicNamespaceService service;

    /** 分页列表：?current=1&pageSize=10&code=&name= */
    @GetMapping
    public Result<List<BasicNamespace>> list(@RequestParam(defaultValue = "1") int current,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String code,
                                             @RequestParam(required = false) String name) {
        Page<BasicNamespace> page = service.list(code, name, current, pageSize);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Result<BasicNamespace> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "命名空间不存在"));
    }

    @PostMapping
    public Result<BasicNamespace> create(@RequestBody BasicNamespace ns) {
        try {
            return Result.success(service.create(ns));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<BasicNamespace> update(@PathVariable Long id, @RequestBody BasicNamespace ns) {
        try {
            return Result.success(service.update(id, ns));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 启用：status = 1 */
    @PutMapping("/{id}/enable")
    public Result<BasicNamespace> enable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 1));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 禁用：status = 0 */
    @PutMapping("/{id}/disable")
    public Result<BasicNamespace> disable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 0));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 全量列表（下拉选择用） */
    @GetMapping("/list")
    public Result<List<BasicNamespace>> listAll() {
        return Result.success(service.listAll());
    }
}
