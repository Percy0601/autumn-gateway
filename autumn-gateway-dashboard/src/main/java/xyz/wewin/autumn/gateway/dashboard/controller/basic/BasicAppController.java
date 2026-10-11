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
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicApp;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicAppService;

import java.util.List;

/**
 * 应用管理（basic_app），替代旧的 /api/system/app。
 */
@RestController
@RequestMapping("/api/system/basic/app")
public class BasicAppController {

    @Autowired
    private BasicAppService service;

    /** 分页列表：?current=1&pageSize=10&appCode=&name= */
    @GetMapping
    public Result<List<BasicApp>> list(@RequestParam(defaultValue = "1") int current,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String appCode,
                                       @RequestParam(required = false) String name) {
        Page<BasicApp> page = service.list(appCode, name, current, pageSize);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Result<BasicApp> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "应用不存在"));
    }

    @PostMapping
    public Result<BasicApp> create(@RequestBody BasicApp app) {
        try {
            return Result.success(service.create(app));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<BasicApp> update(@PathVariable Long id, @RequestBody BasicApp app) {
        try {
            return Result.success(service.update(id, app));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 启用：status = 1 */
    @PutMapping("/{id}/enable")
    public Result<BasicApp> enable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 1));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 禁用：status = 0 */
    @PutMapping("/{id}/disable")
    public Result<BasicApp> disable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 0));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 全量列表（下拉选择用） */
    @GetMapping("/list")
    public Result<List<BasicApp>> listAll() {
        return Result.success(service.listAll());
    }
}
