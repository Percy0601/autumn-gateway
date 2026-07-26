package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.Resource;
import xyz.wewin.autumn.gateway.dashboard.service.ResourceService;

import java.util.List;

@RestController
@RequestMapping("/api/system/resource")
public class ResourceController {

    @Autowired
    private ResourceService resourceService;

    @GetMapping
    public Result<List<Resource>> list(@RequestParam(defaultValue = "1") int current,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) Long appId,
                                       @RequestParam(required = false) String resType,
                                       @RequestParam(required = false) String matchType,
                                       @RequestParam(required = false) String name) {
        Page<Resource> page = resourceService.list(current, pageSize, appId, resType, matchType, name);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Result<Resource> getById(@PathVariable Long id) {
        return resourceService.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "资源不存在"));
    }

    @PostMapping
    public Result<Resource> create(@RequestBody Resource resource) {
        return Result.success(resourceService.create(resource));
    }

    @PutMapping("/{id}")
    public Result<Resource> update(@PathVariable Long id, @RequestBody Resource resource) {
        try {
            return Result.success(resourceService.update(id, resource));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            resourceService.delete(id);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<Resource>> listAll() {
        return Result.success(resourceService.listAll());
    }

}