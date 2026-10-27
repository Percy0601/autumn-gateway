package xyz.wewin.autumn.gateway.dashboard.controller.gw;

import org.springframework.beans.factory.annotation.Autowired;
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
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigCenter;
import xyz.wewin.autumn.gateway.dashboard.repo.GwConfigCenterRepository;
import xyz.wewin.autumn.gateway.dashboard.service.gw.GwDiscoveryService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 配置中心连接管理 + 服务发现代理。
 */
@RestController
@RequestMapping("/api/system/gateway/center")
public class GwConfigCenterController {

    @Autowired
    private GwConfigCenterRepository repo;
    @Autowired
    private GwDiscoveryService discovery;

    @GetMapping
    public Result<List<GwConfigCenter>> list() {
        return Result.success(repo.findAllByStatus(1));
    }

    @PostMapping
    public Result<GwConfigCenter> create(@RequestBody GwConfigCenter c) {
        c.setId(null);
        if (c.getStatus() == null) c.setStatus(1);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return Result.success(repo.save(c));
    }

    @PutMapping("/{id}")
    public Result<GwConfigCenter> update(@PathVariable Long id, @RequestBody GwConfigCenter req) {
        GwConfigCenter existing = repo.findById(id).orElseThrow();
        if (req.getCenterCode() != null) existing.setCenterCode(req.getCenterCode());
        if (req.getCenterName() != null) existing.setCenterName(req.getCenterName());
        if (req.getCenterType() != null) existing.setCenterType(req.getCenterType());
        if (req.getHost() != null) existing.setHost(req.getHost());
        if (req.getPort() != null) existing.setPort(req.getPort());
        if (req.getAclToken() != null) existing.setAclToken(req.getAclToken());
        if (req.getNamespace() != null) existing.setNamespace(req.getNamespace());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        return Result.success(repo.save(existing));
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return Result.success(null);
    }

    /** 从指定配置中心拉已注册的服务名列表 */
    @GetMapping("/{id}/services")
    public Result<List<String>> services(@PathVariable Long id) {
        GwConfigCenter center = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置中心不存在: " + id));
        try {
            return Result.success(discovery.listServices(center));
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
