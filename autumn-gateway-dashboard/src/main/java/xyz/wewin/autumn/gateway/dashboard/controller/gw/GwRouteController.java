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
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwFilterTemplate;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwRoute;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwRouteFilter;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwRoutePredicate;
import xyz.wewin.autumn.gateway.dashboard.repo.GwFilterTemplateRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwRouteFilterRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwRoutePredicateRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwRouteRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 网关路由管理：/api/system/gateway/route/**
 * 含路由本体 + 内嵌断言/过滤器维护。
 */
@RestController
@RequestMapping("/api/system/gateway/route")
public class GwRouteController {

    @Autowired
    private GwRouteRepository routeRepo;
    @Autowired
    private GwRoutePredicateRepository predicateRepo;
    @Autowired
    private GwRouteFilterRepository filterRepo;
    @Autowired
    private GwFilterTemplateRepository filterTemplateRepo;

    @GetMapping
    public Result<List<GwRoute>> list() {
        return Result.success(routeRepo.findAllActive());
    }

    @PostMapping
    public Result<GwRoute> create(@RequestBody GwRoute route) {
        if (route.getRouteCode() == null || route.getRouteCode().isBlank()) {
            return Result.error(400, "路由 id 不能为空");
        }
        if (route.getUri() == null || route.getUri().isBlank()) {
            return Result.error(400, "uri 不能为空");
        }
        if (routeRepo.countByRouteCode(route.getRouteCode()) > 0) {
            return Result.error(400, "路由 id 已存在: " + route.getRouteCode());
        }
        route.setId(null);
        if (route.getStatus() == null) route.setStatus(1);
        if (route.getOrderNum() == null) route.setOrderNum(0);
        route.setDelFlag(0);
        route.setCreatedAt(LocalDateTime.now());
        route.setUpdatedAt(LocalDateTime.now());
        return Result.success(routeRepo.save(route));
    }

    @PutMapping("/{id}")
    public Result<GwRoute> update(@PathVariable Long id, @RequestBody GwRoute req) {
        GwRoute existing = routeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("路由不存在: " + id));
        if (req.getRouteCode() != null) {
            if (routeRepo.countByIdNotAndRouteCode(id, req.getRouteCode()) > 0) {
                return Result.error(400, "路由 id 已存在: " + req.getRouteCode());
            }
            existing.setRouteCode(req.getRouteCode());
        }
        if (req.getRouteName() != null) existing.setRouteName(req.getRouteName());
        if (req.getCenterId() != null) existing.setCenterId(req.getCenterId());
        if (req.getNamespace() != null) existing.setNamespace(req.getNamespace());
        if (req.getServiceName() != null) existing.setServiceName(req.getServiceName());
        if (req.getUri() != null) existing.setUri(req.getUri());
        if (req.getOrderNum() != null) existing.setOrderNum(req.getOrderNum());
        if (req.getMetadataJson() != null) existing.setMetadataJson(req.getMetadataJson());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        return Result.success(routeRepo.save(existing));
    }

    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        GwRoute existing = routeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("路由不存在: " + id));
        existing.setDelFlag(1);
        existing.setUpdatedAt(LocalDateTime.now());
        routeRepo.save(existing);
        return Result.success(null);
    }

    // ---- 断言 ----
    @GetMapping("/{id}/predicates")
    public Result<List<GwRoutePredicate>> predicates(@PathVariable Long id) {
        return Result.success(predicateRepo.findByRouteId(id));
    }

    @PostMapping("/{id}/predicates")
    public Result<GwRoutePredicate> addPredicate(@PathVariable Long id, @RequestBody GwRoutePredicate p) {
        p.setId(null);
        p.setRouteId(id);
        if (p.getStatus() == null) p.setStatus(1);
        p.setDelFlag(0);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return Result.success(predicateRepo.save(p));
    }

    @PutMapping("/predicates/{pid}")
    public Result<GwRoutePredicate> updatePredicate(@PathVariable Long pid, @RequestBody GwRoutePredicate p) {
        GwRoutePredicate existing = predicateRepo.findById(pid)
                .orElseThrow(() -> new IllegalArgumentException("断言不存在: " + pid));
        if (p.getPredicateName() != null) existing.setPredicateName(p.getPredicateName());
        if (p.getArgsJson() != null) existing.setArgsJson(p.getArgsJson());
        if (p.getSortNum() != null) existing.setSortNum(p.getSortNum());
        if (p.getStatus() != null) existing.setStatus(p.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return Result.success(predicateRepo.save(existing));
    }

    @DeleteMapping("/predicates/{pid}")
    public Result<Object> deletePredicate(@PathVariable Long pid) {
        GwRoutePredicate existing = predicateRepo.findById(pid)
                .orElseThrow(() -> new IllegalArgumentException("断言不存在: " + pid));
        existing.setDelFlag(1);
        existing.setUpdatedAt(LocalDateTime.now());
        predicateRepo.save(existing);
        return Result.success(null);
    }

    // ---- 路由过滤器 ----
    @GetMapping("/{id}/filters")
    public Result<List<GwRouteFilter>> filters(@PathVariable Long id) {
        return Result.success(filterRepo.findByRouteId(id));
    }

    @PostMapping("/{id}/filters")
    public Result<GwRouteFilter> addFilter(@PathVariable Long id, @RequestBody GwRouteFilter f) {
        if (f.getFilterName() == null || f.getFilterName().isBlank()) {
            return Result.error(400, "filter_name 不能为空");
        }
        // 「一次」约束：模板 singleton_per_route=1 的过滤器，同一路由只允许挂一次
        GwFilterTemplate tpl = filterTemplateRepo.findByFilterName(f.getFilterName()).orElse(null);
        if (tpl != null && Integer.valueOf(1).equals(tpl.getSingletonPerRoute())) {
            boolean exists = filterRepo.findByRouteId(id).stream()
                    .anyMatch(x -> f.getFilterName().equals(x.getFilterName()));
            if (exists) {
                return Result.error(400, "过滤器 " + f.getFilterName() + " 每路由只允许挂一次");
            }
        }
        f.setId(null);
        f.setRouteId(id);
        if (f.getStatus() == null) f.setStatus(1);
        if (f.getSortNum() == null) f.setSortNum(0);
        f.setDelFlag(0);
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        return Result.success(filterRepo.save(f));
    }

    @PutMapping("/filters/{fid}")
    public Result<GwRouteFilter> updateFilter(@PathVariable Long fid, @RequestBody GwRouteFilter f) {
        GwRouteFilter existing = filterRepo.findById(fid)
                .orElseThrow(() -> new IllegalArgumentException("过滤器不存在: " + fid));
        if (f.getFilterName() != null) existing.setFilterName(f.getFilterName());
        if (f.getArgsJson() != null) existing.setArgsJson(f.getArgsJson());
        if (f.getSortNum() != null) existing.setSortNum(f.getSortNum());
        if (f.getStatus() != null) existing.setStatus(f.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return Result.success(filterRepo.save(existing));
    }

    @DeleteMapping("/filters/{fid}")
    public Result<Object> deleteFilter(@PathVariable Long fid) {
        GwRouteFilter existing = filterRepo.findById(fid)
                .orElseThrow(() -> new IllegalArgumentException("过滤器不存在: " + fid));
        existing.setDelFlag(1);
        existing.setUpdatedAt(LocalDateTime.now());
        filterRepo.save(existing);
        return Result.success(null);
    }
}
