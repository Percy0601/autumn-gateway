package xyz.wewin.autumn.gateway.dashboard.controller.gw;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.service.gw.GwConfigSyncService;

import java.util.Map;

/**
 * 配置同步：聚合预览全部网关配置 / 推送到配置中心 / 下载（下载由前端基于预览文本生成文件）。
 *
 * <p>路径 /api/system/gateway/sync/**：preview（properties 文本 + 数量统计）、publish（Consul KV / Nacos）。
 */
@RestController
@RequestMapping("/api/system/gateway/sync")
public class GwConfigSyncController {

    private final GwConfigSyncService service;

    public GwConfigSyncController(GwConfigSyncService service) {
        this.service = service;
    }

    /** 聚合预览：{ profile, text, counts:{configItems,structs,globalFilters,routes,predicates,filters}, warn[] } */
    @GetMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestParam(required = false) String profile) {
        return Result.success(service.preview(profile));
    }

    /** 推送到配置中心：body { profile, centerId }，返回单中心推送结果 */
    @PostMapping("/publish")
    public Result<Map<String, Object>> publish(@RequestBody Map<String, Object> req) {
        String profile = req.get("profile") == null ? null : String.valueOf(req.get("profile"));
        Object cid = req.get("centerId");
        if (cid == null) throw new IllegalArgumentException("centerId 不能为空");
        return Result.success(service.publish(profile, Long.valueOf(String.valueOf(cid))));
    }
}
