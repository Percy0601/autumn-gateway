package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.Application;
import xyz.wewin.autumn.gateway.dashboard.service.ApplicationService;

import java.util.List;

@RestController
@RequestMapping("/api/system/app")
public class ApplicationController {
    @Autowired
    private ApplicationService applicationService;

    /**
     * 分页列表（ProTable 格式）
     * 请求参数：?current=1&pageSize=10&appid=&name=
     */
    @GetMapping("/list")
    public Result<List<Application>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String appid,
            @RequestParam(required = false) String name) {
        Page<Application> page = applicationService.list(current, pageSize);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    /**
     * 获取单个应用详情
     */
    @GetMapping("/{id}")
    public Result<Application> getById(@PathVariable Long id) {
        return applicationService.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "应用不存在"));
    }

    /**
     * 新增应用
     */
    @PostMapping
    public Result<Application> create(@RequestBody Application app) {
        try {
            Application saved = applicationService.create(app);
            return Result.success(saved);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新应用
     */
    @PutMapping("/{id}")
    public Result<Application> update(@PathVariable Long id, @RequestBody Application app) {
        try {
            Application updated = applicationService.update(id, app);
            return Result.success(updated);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 全量列表（供下拉选择使用）
     */
    @GetMapping("/list-all")
    public Result<List<Application>> listAll() {
        return Result.success(applicationService.listAll());
    }
}
