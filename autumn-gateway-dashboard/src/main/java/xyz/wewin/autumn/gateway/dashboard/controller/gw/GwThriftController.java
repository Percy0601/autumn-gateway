package xyz.wewin.autumn.gateway.dashboard.controller.gw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwThriftConversion;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwThriftService;
import xyz.wewin.autumn.gateway.dashboard.repo.GwThriftConversionRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwThriftServiceRepository;
import xyz.wewin.autumn.gateway.dashboard.service.gw.GwThriftConfigService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 协议转换（HTTP → Thrift 泛化调用）配置管理：/api/system/gateway/thrift/**
 *
 * <p>两部分配置：</p>
 * <ul>
 *   <li>services：thrift 服务端点 + IDL（ShenYu 的 metadata 位置）；</li>
 *   <li>conversions：HTTP 入参/出参映射规则（ShenYu 的 RpcParamTransform + param-mapping 位置）。</li>
 * </ul>
 * 页面配置落库后，由 {@link GwThriftConfigService} 导出成 {@code autumn.thrift.*}，
 * 随「配置同步」一起下发到本地文件 / Consul / Nacos。
 */
@RestController
@RequestMapping("/api/system/gateway/thrift")
public class GwThriftController {

    @Autowired
    private GwThriftServiceRepository serviceRepo;
    @Autowired
    private GwThriftConversionRepository conversionRepo;
    @Autowired
    private GwThriftConfigService configService;

    // ------------------------------------------------------------------ 服务

    @GetMapping("/services")
    public Result<List<GwThriftService>> listServices() {
        return Result.success(serviceRepo.findAllActive());
    }

    @PreAuthorize("@permissions.canManageGateway()")
    @PostMapping("/services")
    public Result<GwThriftService> createService(@RequestBody GwThriftService req) {
        if (!StringUtils.hasText(req.getServiceCode())) {
            return Result.error(400, "service_code 不能为空（必须等于 IDL 里的 service 名）");
        }
        if (serviceRepo.findByServiceCode(req.getServiceCode()).isPresent()) {
            return Result.error(400, "service_code 已存在: " + req.getServiceCode());
        }
        if (!StringUtils.hasText(req.getIdlContent()) && !StringUtils.hasText(req.getIdlLocation())) {
            return Result.error(400, "idl_content 与 idl_location 至少填一个");
        }
        if (!StringUtils.hasText(req.getHost()) || req.getPort() == null || req.getPort() <= 0) {
            return Result.error(400, "host / port 不能为空");
        }
        req.setId(null);
        if (req.getFramed() == null) req.setFramed(1);
        if (req.getMultiplexed() == null) req.setMultiplexed(0);
        if (req.getStatus() == null) req.setStatus(1);
        req.setDelFlag(0);
        req.setCreatedAt(LocalDateTime.now());
        req.setUpdatedAt(LocalDateTime.now());
        return Result.success(serviceRepo.save(req));
    }

    @PreAuthorize("@permissions.canManageGateway()")
    @PutMapping("/services/{id}")
    public Result<GwThriftService> updateService(@PathVariable Long id, @RequestBody GwThriftService req) {
        GwThriftService existing = serviceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("thrift 服务不存在: " + id));
        if (StringUtils.hasText(req.getServiceCode())) {
            serviceRepo.findByServiceCode(req.getServiceCode())
                    .filter(s -> !s.getId().equals(id))
                    .ifPresent(s -> {
                        throw new IllegalArgumentException("service_code 已存在: " + req.getServiceCode());
                    });
            existing.setServiceCode(req.getServiceCode());
        }
        if (StringUtils.hasText(req.getServiceName())) existing.setServiceName(req.getServiceName());
        if (StringUtils.hasText(req.getHost())) existing.setHost(req.getHost());
        if (req.getPort() != null) existing.setPort(req.getPort());
        existing.setIdlContent(req.getIdlContent());
        existing.setIdlLocation(req.getIdlLocation() == null ? "" : req.getIdlLocation());
        if (req.getFramed() != null) existing.setFramed(req.getFramed());
        if (req.getMultiplexed() != null) existing.setMultiplexed(req.getMultiplexed());
        if (req.getMultiplexedName() != null) existing.setMultiplexedName(req.getMultiplexedName());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        return Result.success(serviceRepo.save(existing));
    }

    @PreAuthorize("@permissions.canManageGateway()")
    @DeleteMapping("/services/{id}")
    public Result<Void> deleteService(@PathVariable Long id) {
        GwThriftService existing = serviceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("thrift 服务不存在: " + id));
        existing.setDelFlag(1);
        existing.setUpdatedAt(LocalDateTime.now());
        serviceRepo.save(existing);
        return Result.success(null);
    }

    // ------------------------------------------------------------------ 转换规则

    @GetMapping("/conversions")
    public Result<List<GwThriftConversion>> listConversions() {
        return Result.success(conversionRepo.findAllActive());
    }

    @PreAuthorize("@permissions.canManageGateway()")
    @PostMapping("/conversions")
    public Result<GwThriftConversion> createConversion(@RequestBody GwThriftConversion req) {
        if (!StringUtils.hasText(req.getConversionCode())) {
            return Result.error(400, "conversion_code 不能为空（路由过滤器第三参要引用它）");
        }
        if (conversionRepo.findByConversionCode(req.getConversionCode()).isPresent()) {
            return Result.error(400, "conversion_code 已存在: " + req.getConversionCode());
        }
        if (!StringUtils.hasText(req.getServiceCode())) {
            return Result.error(400, "service_code 不能为空");
        }
        req.setId(null);
        if (req.getIncludeBody() == null) req.setIncludeBody(1);
        if (!StringUtils.hasText(req.getResponseMode())) req.setResponseMode("ENVELOPE");
        if (req.getSuccessStatus() == null) req.setSuccessStatus(0);
        if (req.getStatus() == null) req.setStatus(1);
        req.setDelFlag(0);
        req.setCreatedAt(LocalDateTime.now());
        req.setUpdatedAt(LocalDateTime.now());
        return Result.success(conversionRepo.save(req));
    }

    @PreAuthorize("@permissions.canManageGateway()")
    @PutMapping("/conversions/{id}")
    public Result<GwThriftConversion> updateConversion(@PathVariable Long id,
                                                       @RequestBody GwThriftConversion req) {
        GwThriftConversion existing = conversionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("转换规则不存在: " + id));
        if (StringUtils.hasText(req.getConversionCode())) {
            conversionRepo.findByConversionCode(req.getConversionCode())
                    .filter(c -> !c.getId().equals(id))
                    .ifPresent(c -> {
                        throw new IllegalArgumentException("conversion_code 已存在: " + req.getConversionCode());
                    });
            existing.setConversionCode(req.getConversionCode());
        }
        if (req.getConversionName() != null) existing.setConversionName(req.getConversionName());
        if (StringUtils.hasText(req.getServiceCode())) existing.setServiceCode(req.getServiceCode());
        if (req.getMethodName() != null) existing.setMethodName(req.getMethodName());
        if (req.getHttpPath() != null) existing.setHttpPath(req.getHttpPath());
        if (req.getIncludeBody() != null) existing.setIncludeBody(req.getIncludeBody());
        existing.setArgumentsJson(req.getArgumentsJson());
        if (StringUtils.hasText(req.getResponseMode())) existing.setResponseMode(req.getResponseMode());
        if (req.getSuccessStatus() != null) existing.setSuccessStatus(req.getSuccessStatus());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        return Result.success(conversionRepo.save(existing));
    }

    @PreAuthorize("@permissions.canManageGateway()")
    @DeleteMapping("/conversions/{id}")
    public Result<Void> deleteConversion(@PathVariable Long id) {
        GwThriftConversion existing = conversionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("转换规则不存在: " + id));
        existing.setDelFlag(1);
        existing.setUpdatedAt(LocalDateTime.now());
        conversionRepo.save(existing);
        return Result.success(null);
    }

    // ------------------------------------------------------------------ 导出

    /** 预览 autumn.thrift.* 配置块 */
    @GetMapping("/preview")
    public Result<Map<String, Object>> preview() {
        return Result.success(configService.preview());
    }
}
