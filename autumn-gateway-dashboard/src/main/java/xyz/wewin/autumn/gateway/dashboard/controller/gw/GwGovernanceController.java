package xyz.wewin.autumn.gateway.dashboard.controller.gw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwServiceInstance;
import xyz.wewin.autumn.gateway.dashboard.repo.GwServiceInstanceRepository;
import xyz.wewin.autumn.gateway.dashboard.service.gw.GwGovernanceService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 服务治理：观测 / 度量 autumn service framework 服务实例。
 *
 * <p>实例登记在 gw_service_instance；观测数据不落库，每次请求实时远程 invoke
 * 目标实例的控制面 ControlApi（Thrift，默认 30880），因此页面看到的是当前真实状态。</p>
 */
@RestController
@RequestMapping("/api/system/gateway/governance")
public class GwGovernanceController {

    @Autowired
    private GwServiceInstanceRepository instanceRepo;
    @Autowired
    private GwGovernanceService governanceService;

    /** 控制面方法清单（前端 invoke 控制台用） */
    @GetMapping("/methods")
    public Result<List<Map<String, Object>>> methods() {
        return Result.success(governanceService.methods());
    }

    /** 首屏 / 定时刷新：所有启用实例的健康 + 应用信息 + 度量 */
    @GetMapping("/overview")
    public Result<List<Map<String, Object>>> overview() {
        return Result.success(governanceService.overview());
    }

    /** 单实例观测（详情抽屉用） */
    @GetMapping("/instances/{id}/observe")
    public Result<Map<String, Object>> observe(@PathVariable Long id) {
        GwServiceInstance instance = requireActive(id);
        return Result.success(governanceService.observe(instance));
    }

    /** 手动 invoke：{ instanceId, method, params:{...} } → 原始返回串 */
    @PreAuthorize("@permissions.canManageGateway()")
    @PostMapping("/invoke")
    public Result<Map<String, Object>> invoke(@RequestBody Map<String, Object> req) {
        if (req == null || req.get("instanceId") == null) {
            return Result.error(400, "instanceId 不能为空");
        }
        Long id = Long.valueOf(String.valueOf(req.get("instanceId")));
        String method = req.get("method") == null ? "" : String.valueOf(req.get("method"));
        @SuppressWarnings("unchecked")
        Map<String, Object> params = req.get("params") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        long start = System.currentTimeMillis();
        String raw = governanceService.invoke(requireActive(id), method, params);
        return Result.success(Map.of(
                "method", method,
                "result", raw == null ? "" : raw,
                "elapsedMillis", System.currentTimeMillis() - start));
    }

    // ------------------------------------------------------------------ 实例登记

    @GetMapping("/instances")
    public Result<List<GwServiceInstance>> list() {
        return Result.success(instanceRepo.findAllActive());
    }

    @PreAuthorize("@permissions.canManageGateway()")
    @PostMapping("/instances")
    public Result<GwServiceInstance> create(@RequestBody GwServiceInstance req) {
        String error = validate(req);
        if (error != null) {
            return Result.error(400, error);
        }
        req.setId(null);
        if (req.getPort() == null || req.getPort() <= 0) {
            req.setPort(30880);
        }
        if (req.getStatus() == null) {
            req.setStatus(1);
        }
        req.setDelFlag(0);
        LocalDateTime now = LocalDateTime.now();
        req.setCreatedAt(now);
        req.setUpdatedAt(now);
        return Result.success(instanceRepo.save(req));
    }

    @PreAuthorize("@permissions.canManageGateway()")
    @PutMapping("/instances/{id}")
    public Result<GwServiceInstance> update(@PathVariable Long id, @RequestBody GwServiceInstance req) {
        GwServiceInstance existing = requireActive(id);
        String error = validate(req);
        if (error != null) {
            return Result.error(400, error);
        }
        existing.setServiceName(req.getServiceName().trim());
        existing.setHost(req.getHost().trim());
        existing.setPort(req.getPort() == null || req.getPort() <= 0 ? 30880 : req.getPort());
        existing.setControlPort(req.getControlPort());
        existing.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        existing.setRemark(req.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        return Result.success(instanceRepo.save(existing));
    }

    /** 逻辑删除，保留历史登记记录 */
    @PreAuthorize("@permissions.canManageGateway()")
    @DeleteMapping("/instances/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        GwServiceInstance existing = requireActive(id);
        existing.setDelFlag(1);
        existing.setUpdatedAt(LocalDateTime.now());
        instanceRepo.save(existing);
        return Result.success(null);
    }

    private GwServiceInstance requireActive(Long id) {
        return instanceRepo.findActiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("服务实例不存在: " + id));
    }

    private static String validate(GwServiceInstance req) {
        if (req == null || !StringUtils.hasText(req.getServiceName())) {
            return "服务名不能为空";
        }
        if (!StringUtils.hasText(req.getHost())) {
            return "host 不能为空";
        }
        if (req.getControlPort() != null && (req.getControlPort() < 0 || req.getControlPort() > 65535)) {
            return "controlPort 不合法";
        }
        if (req.getPort() != null && (req.getPort() < 0 || req.getPort() > 65535)) {
            return "port 不合法";
        }
        return null;
    }
}
