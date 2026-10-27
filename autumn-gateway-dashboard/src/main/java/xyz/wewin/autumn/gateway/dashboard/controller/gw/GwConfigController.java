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
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigItem;
import xyz.wewin.autumn.gateway.dashboard.repo.GwConfigItemRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 网关全局配置项：/api/system/gateway/config/**
 */
@RestController
@RequestMapping("/api/system/gateway/config")
public class GwConfigController {

    @Autowired
    private GwConfigItemRepository repo;

    @GetMapping
    public Result<List<GwConfigItem>> list(@RequestParam(defaultValue = "dev") String profile) {
        return Result.success(repo.findByProfile(profile));
    }

    @PostMapping
    public Result<GwConfigItem> create(@RequestBody GwConfigItem item) {
        if (item.getConfigKey() == null || item.getConfigKey().isBlank()) {
            return Result.error(400, "config_key 不能为空");
        }
        if (item.getProfileCode() == null) item.setProfileCode("dev");
        item.setId(null);
        if (item.getValueType() == null) item.setValueType("STRING");
        if (item.getIsRequired() == null) item.setIsRequired(0);
        if (item.getIsEncrypted() == null) item.setIsEncrypted(0);
        if (item.getSortNum() == null) item.setSortNum(0);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return Result.success(repo.save(item));
    }

    @PutMapping("/{id}")
    public Result<GwConfigItem> update(@PathVariable Long id, @RequestBody GwConfigItem req) {
        GwConfigItem existing = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置项不存在: " + id));
        if (req.getConfigKey() != null) existing.setConfigKey(req.getConfigKey());
        if (req.getConfigGroup() != null) existing.setConfigGroup(req.getConfigGroup());
        if (req.getConfigValue() != null) existing.setConfigValue(req.getConfigValue());
        if (req.getValueType() != null) existing.setValueType(req.getValueType());
        if (req.getIsRequired() != null) existing.setIsRequired(req.getIsRequired());
        if (req.getIsEncrypted() != null) existing.setIsEncrypted(req.getIsEncrypted());
        if (req.getSortNum() != null) existing.setSortNum(req.getSortNum());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        return Result.success(repo.save(existing));
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return Result.success(null);
    }
}
