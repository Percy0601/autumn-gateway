package xyz.wewin.autumn.gateway.dashboard.controller.gw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwGlobalFilter;
import xyz.wewin.autumn.gateway.dashboard.repo.GwGlobalFilterRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 全局默认过滤器：/api/system/gateway/global-filter/**
 */
@RestController
@RequestMapping("/api/system/gateway/global-filter")
public class GwGlobalFilterController {

    @Autowired
    private GwGlobalFilterRepository repo;

    @GetMapping
    public Result<List<GwGlobalFilter>> list() {
        return Result.success(repo.findAllActive());
    }

    @PostMapping
    public Result<GwGlobalFilter> create(@RequestBody GwGlobalFilter f) {
        if (f.getFilterName() == null || f.getFilterName().isBlank()) {
            return Result.error(400, "filter_name 不能为空");
        }
        f.setId(null);
        if (f.getSortNum() == null) f.setSortNum(0);
        if (f.getEnabled() == null) f.setEnabled(1);
        if (f.getStatus() == null) f.setStatus(1);
        f.setDelFlag(0);
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        return Result.success(repo.save(f));
    }

    @PutMapping("/{id}")
    public Result<GwGlobalFilter> update(@PathVariable Long id, @RequestBody GwGlobalFilter req) {
        GwGlobalFilter existing = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("过滤器不存在: " + id));
        if (req.getFilterName() != null) existing.setFilterName(req.getFilterName());
        if (req.getArgsJson() != null) existing.setArgsJson(req.getArgsJson());
        if (req.getSortNum() != null) existing.setSortNum(req.getSortNum());
        if (req.getEnabled() != null) existing.setEnabled(req.getEnabled());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        return Result.success(repo.save(existing));
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        GwGlobalFilter existing = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("过滤器不存在: " + id));
        existing.setDelFlag(1);
        existing.setUpdatedAt(LocalDateTime.now());
        repo.save(existing);
        return Result.success(null);
    }
}
