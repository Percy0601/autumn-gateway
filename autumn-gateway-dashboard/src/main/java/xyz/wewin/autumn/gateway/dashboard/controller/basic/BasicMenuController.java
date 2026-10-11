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
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenu;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicMenuService;

import java.util.List;

/**
 * 菜单管理（basic_menu）。
 * 前端按 parentId 自行构建树，这里返回扁平列表。
 */
@RestController
@RequestMapping("/api/system/basic/menu")
public class BasicMenuController {

    @Autowired
    private BasicMenuService service;

    /** 分页列表：?current=1&pageSize=9999&appId= */
    @GetMapping
    public Result<List<BasicMenu>> list(@RequestParam(defaultValue = "1") int current,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) Integer appId) {
        Page<BasicMenu> page = service.list(appId, current, pageSize);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    /** 按应用返回全量菜单 */
    @GetMapping("/list")
    public Result<List<BasicMenu>> listByApp(@RequestParam(required = false) Integer appId) {
        return Result.success(service.listByApp(appId));
    }

    @GetMapping("/{id}")
    public Result<BasicMenu> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "菜单不存在"));
    }

    @PostMapping
    public Result<BasicMenu> create(@RequestBody BasicMenu menu) {
        try {
            return Result.success(service.create(menu));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<BasicMenu> update(@PathVariable Long id, @RequestBody BasicMenu menu) {
        try {
            return Result.success(service.update(id, menu));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 启用：menu_status = 1 */
    @PutMapping("/{id}/enable")
    public Result<BasicMenu> enable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 1));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 停用：menu_status = 2 */
    @PutMapping("/{id}/disable")
    public Result<BasicMenu> disable(@PathVariable Long id) {
        try {
            return Result.success(service.updateStatus(id, 2));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
