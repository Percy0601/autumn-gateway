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
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwPredicateTemplate;
import xyz.wewin.autumn.gateway.dashboard.repo.GwFilterTemplateRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwPredicateTemplateRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 过滤器/断言模板管理。
 */
@RestController
@RequestMapping("/api/system/gateway/template")
public class GwTemplateController {

    @Autowired
    private GwFilterTemplateRepository filterRepo;
    @Autowired
    private GwPredicateTemplateRepository predicateRepo;

    @GetMapping("/filter")
    public Result<List<GwFilterTemplate>> filterTemplates() {
        return Result.success(filterRepo.findByStatus(1));
    }

    @PostMapping("/filter")
    public Result<GwFilterTemplate> createFilter(@RequestBody GwFilterTemplate t) {
        t.setId(null);
        if (t.getStatus() == null) t.setStatus(1);
        if (t.getSingletonPerRoute() == null) t.setSingletonPerRoute(0);
        if (t.getScope() == null) t.setScope("ROUTE");
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return Result.success(filterRepo.save(t));
    }

    @PutMapping("/filter/{id}")
    public Result<GwFilterTemplate> updateFilter(@PathVariable Long id, @RequestBody GwFilterTemplate req) {
        GwFilterTemplate e = filterRepo.findById(id).orElseThrow();
        if (req.getFilterName() != null) e.setFilterName(req.getFilterName());
        if (req.getDisplayName() != null) e.setDisplayName(req.getDisplayName());
        if (req.getScope() != null) e.setScope(req.getScope());
        if (req.getSingletonPerRoute() != null) e.setSingletonPerRoute(req.getSingletonPerRoute());
        if (req.getArgsSchemaJson() != null) e.setArgsSchemaJson(req.getArgsSchemaJson());
        if (req.getRemark() != null) e.setRemark(req.getRemark());
        e.setUpdatedAt(LocalDateTime.now());
        return Result.success(filterRepo.save(e));
    }

    @DeleteMapping("/filter/{id}")
    public Result<Object> deleteFilter(@PathVariable Long id) {
        filterRepo.deleteById(id);
        return Result.success(null);
    }

    @GetMapping("/predicate")
    public Result<List<GwPredicateTemplate>> predicateTemplates() {
        return Result.success(predicateRepo.findByStatus(1));
    }

    @PostMapping("/predicate")
    public Result<GwPredicateTemplate> createPredicate(@RequestBody GwPredicateTemplate t) {
        t.setId(null);
        if (t.getStatus() == null) t.setStatus(1);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return Result.success(predicateRepo.save(t));
    }

    @PutMapping("/predicate/{id}")
    public Result<GwPredicateTemplate> updatePredicate(@PathVariable Long id, @RequestBody GwPredicateTemplate req) {
        GwPredicateTemplate e = predicateRepo.findById(id).orElseThrow();
        if (req.getPredicateName() != null) e.setPredicateName(req.getPredicateName());
        if (req.getDisplayName() != null) e.setDisplayName(req.getDisplayName());
        if (req.getArgsSchemaJson() != null) e.setArgsSchemaJson(req.getArgsSchemaJson());
        if (req.getRemark() != null) e.setRemark(req.getRemark());
        e.setUpdatedAt(LocalDateTime.now());
        return Result.success(predicateRepo.save(e));
    }

    @DeleteMapping("/predicate/{id}")
    public Result<Object> deletePredicate(@PathVariable Long id) {
        predicateRepo.deleteById(id);
        return Result.success(null);
    }
}
