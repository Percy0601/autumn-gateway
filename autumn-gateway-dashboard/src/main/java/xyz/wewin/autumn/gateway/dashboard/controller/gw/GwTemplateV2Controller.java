package xyz.wewin.autumn.gateway.dashboard.controller.gw;

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
import xyz.wewin.autumn.gateway.dashboard.dto.gateway.GwTplSaveRequest;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigStruct;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwTplDefinition;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwValueType;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwValueTypeField;
import xyz.wewin.autumn.gateway.dashboard.service.gw.GwTemplateV2Service;

import java.util.List;
import java.util.Map;

/**
 * 模板系统 v2 接口（模板化可视化配置）。
 *
 * <p>路径统一挂在 /api/system/gateway/template-v2/**（与 v1 的 template/filter、template/predicate 并存，
 * v1 待后续下线）。覆盖：值类型目录、模板定义（含字段）、结构化全局配置实例、实例校验与导出预览。
 */
@RestController
@RequestMapping("/api/system/gateway/template-v2")
public class GwTemplateV2Controller {

    private final GwTemplateV2Service service;

    public GwTemplateV2Controller(GwTemplateV2Service service) {
        this.service = service;
    }

    // ---------------- 值类型目录 ----------------

    @GetMapping("/value-types")
    public Result<List<Map<String, Object>>> valueTypes(@RequestParam(required = false) Integer status) {
        return Result.success(service.listValueTypes(status));
    }

    @PostMapping("/value-types")
    public Result<GwValueType> createValueType(@RequestBody GwValueType req) {
        return Result.success(service.createValueType(req));
    }

    @PutMapping("/value-types/{id}")
    public Result<GwValueType> updateValueType(@PathVariable Long id, @RequestBody GwValueType req) {
        return Result.success(service.updateValueType(id, req));
    }

    @DeleteMapping("/value-types/{id}")
    public Result<Void> deleteValueType(@PathVariable Long id) {
        service.deleteValueType(id);
        return Result.success(null);
    }

    /** LIST/OBJECT 类型的结构字段保存（整体替换） */
    @PutMapping("/value-types/{id}/fields")
    public Result<Void> saveTypeFields(@PathVariable Long id, @RequestBody List<GwValueTypeField> fields) {
        service.saveTypeFields(id, fields);
        return Result.success(null);
    }

    // ---------------- 模板定义 ----------------

    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> templates(@RequestParam(required = false) String kind,
                                                       @RequestParam(required = false) Integer status) {
        return Result.success(service.listTemplates(kind, status));
    }

    @GetMapping("/templates/{id}/tree")
    public Result<Map<String, Object>> templateTree(@PathVariable Long id) {
        return Result.success(service.templateTree(id));
    }

    @PostMapping("/templates")
    public Result<GwTplDefinition> createTemplate(@RequestBody GwTplSaveRequest req) {
        return Result.success(service.createTemplate(req));
    }

    @PutMapping("/templates/{id}")
    public Result<GwTplDefinition> updateTemplate(@PathVariable Long id, @RequestBody GwTplSaveRequest req) {
        return Result.success(service.updateTemplate(id, req));
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        service.deleteTemplate(id);
        return Result.success(null);
    }

    // ---------------- STRUCT 实例 ----------------

    @GetMapping("/structs")
    public Result<List<GwConfigStruct>> structs(@RequestParam(required = false) String profile) {
        return Result.success(service.listStructs(profile));
    }

    @PostMapping("/structs")
    public Result<GwConfigStruct> createStruct(@RequestBody GwConfigStruct req) {
        return Result.success(service.createStruct(req));
    }

    @PutMapping("/structs/{id}")
    public Result<GwConfigStruct> updateStruct(@PathVariable Long id, @RequestBody GwConfigStruct req) {
        return Result.success(service.updateStruct(id, req));
    }

    @DeleteMapping("/structs/{id}")
    public Result<Void> deleteStruct(@PathVariable Long id) {
        service.deleteStruct(id);
        return Result.success(null);
    }

    @GetMapping("/structs/{id}/preview")
    public Result<List<String>> structPreview(@PathVariable Long id) {
        return Result.success(service.structPreview(id));
    }

    // ---------------- 校验与导出预览 ----------------

    /** 校验模板实例 JSON，并返回导出预览（FILTER/PREDICATE → args 扁平键；STRUCT → properties 行） */
    @PostMapping("/validate")
    public Result<Map<String, Object>> validate(@RequestBody Map<String, String> req) {
        String kind = req.get("kind");
        String name = req.get("name");
        String valueJson = req.get("valueJson");
        return Result.success(service.validateInstance(kind, name, valueJson));
    }
}
