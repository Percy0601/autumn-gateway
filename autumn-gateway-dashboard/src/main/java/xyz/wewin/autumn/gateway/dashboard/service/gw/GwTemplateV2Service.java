package xyz.wewin.autumn.gateway.dashboard.service.gw;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.wewin.autumn.gateway.dashboard.dto.gateway.GwTplSaveRequest;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigStruct;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwTplDefinition;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwTplField;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwValueType;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwValueTypeField;
import xyz.wewin.autumn.gateway.dashboard.repo.GwConfigStructRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwTplDefinitionRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwTplFieldRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwValueTypeFieldRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.GwValueTypeRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 模板系统 v2 服务：值类型目录 / 模板定义 / 结构化全局配置实例 / 实例校验与导出预览。
 *
 * <p>核心建模（见 docs/gateway/网关可视化配置-模板系统设计.md）：
 * <ul>
 *   <li>gw_value_type + gw_value_type_field（type_id 自引用递归）：BASIC/LIST/OBJECT 值类型目录；
 *       LIST 用 role=ITEM 定义元素类型，OBJECT 用 role=FIELD 定义字段，均可再引用复合类型 → 表达嵌套与 map。</li>
 *   <li>gw_tpl_definition + gw_tpl_field：过滤器/断言/结构化模板；配置项可 is_map_key=1（实例值为
 *       Map，键由 map_key_validator 约束，值类型任意）。</li>
 *   <li>gw_config_struct：STRUCT 模板实例（如 globalcors），value_json 为树形 JSON。</li>
 *   <li>导出：FILTER/PREDICATE 实例 → args 扁平键（列表逗号拼接、嵌套对象点号展开）；
 *       STRUCT 实例 → 完整 properties 行（前缀 + export_key + map 键 ['k'] / 列表 [i]）。</li>
 * </ul>
 */
@Service
public class GwTemplateV2Service {

    private static final String KIND_FILTER = "FILTER";
    private static final String KIND_PREDICATE = "PREDICATE";
    private static final String KIND_STRUCT = "STRUCT";
    private static final String KIND_BASIC = "BASIC";
    private static final String KIND_LIST = "LIST";
    private static final String KIND_OBJECT = "OBJECT";
    private static final String ROLE_ITEM = "ITEM";
    private static final String ROLE_FIELD = "FIELD";

    private final GwValueTypeRepository valueTypeRepo;
    private final GwValueTypeFieldRepository typeFieldRepo;
    private final GwTplDefinitionRepository tplRepo;
    private final GwTplFieldRepository tplFieldRepo;
    private final GwConfigStructRepository structRepo;
    // 与 GwDiscoveryService 同款惯例：Boot 4 首选 Jackson 3，Jackson 2 仅作兼容保留、
    // 不注册为 bean，注入的 ObjectMapper 包名会随版本漂移；统一静态实例直用。
    private static final ObjectMapper om = new ObjectMapper();

    public GwTemplateV2Service(GwValueTypeRepository valueTypeRepo,
                               GwValueTypeFieldRepository typeFieldRepo,
                               GwTplDefinitionRepository tplRepo,
                               GwTplFieldRepository tplFieldRepo,
                               GwConfigStructRepository structRepo) {
        this.valueTypeRepo = valueTypeRepo;
        this.typeFieldRepo = typeFieldRepo;
        this.tplRepo = tplRepo;
        this.tplFieldRepo = tplFieldRepo;
        this.structRepo = structRepo;
    }

    // =====================================================================
    // 值类型目录
    // =====================================================================

    /** 全部类型 + 一层字段（含引用类型的名字/种类，供前端下拉） */
    public List<Map<String, Object>> listValueTypes(Integer status) {
        List<GwValueType> types = status != null
                ? valueTypeRepo.findByStatusOrderBySortNum(status)
                : valueTypeRepo.findAllByOrderByKindAscSortNumAsc();
        List<Map<String, Object>> out = new ArrayList<>();
        for (GwValueType t : types) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("displayName", t.getDisplayName());
            m.put("kind", t.getKind());
            m.put("builtin", t.getBuiltin());
            m.put("sortNum", t.getSortNum());
            m.put("status", t.getStatus());
            m.put("remark", t.getRemark());
            m.put("fields", typeFieldsOneLevel(t.getId()));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public GwValueType createValueType(GwValueType req) {
        validateTypeHeader(req, null);
        GwValueType t = new GwValueType();
        copyType(req, t);
        t.setId(null);
        t.setBuiltin(0);
        if (t.getStatus() == null) t.setStatus(1);
        if (t.getSortNum() == null) t.setSortNum(0);
        LocalDateTime now = LocalDateTime.now();
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        return valueTypeRepo.save(t);
    }

    @Transactional
    public GwValueType updateValueType(Long id, GwValueType req) {
        GwValueType t = mustType(id);
        if (isBuiltin(t)) throw new IllegalArgumentException("内置类型只读，请复制为新类型后编辑");
        validateTypeHeader(req, id);
        copyType(req, t);
        t.setUpdatedAt(LocalDateTime.now());
        return valueTypeRepo.save(t);
    }

    @Transactional
    public void deleteValueType(Long id) {
        GwValueType t = mustType(id);
        if (isBuiltin(t)) throw new IllegalArgumentException("内置类型是模板的基础能力，不允许删除");
        String ref = firstTypeReference(id);
        if (ref != null) throw new IllegalArgumentException("该类型正被「" + ref + "」引用，无法删除");
        typeFieldRepo.deleteByTypeId(id);
        valueTypeRepo.deleteById(id);
    }

    /** LIST/OBJECT 类型的结构字段保存：整体替换 */
    @Transactional
    public void saveTypeFields(Long typeId, List<GwValueTypeField> fields) {
        GwValueType t = mustType(typeId);
        if (KIND_BASIC.equals(t.getKind())) throw new IllegalArgumentException("BASIC 类型没有结构字段");
        if (isBuiltin(t)) throw new IllegalArgumentException("内置类型只读，请复制为新类型后编辑");
        validateTypeFields(t, fields);
        LocalDateTime now = LocalDateTime.now();
        typeFieldRepo.deleteByTypeId(typeId);
        int sort = 0;
        for (GwValueTypeField f : fields) {
            f.setId(null);
            f.setTypeId(typeId);
            if (f.getSortNum() == null) f.setSortNum(sort++);
            if (f.getRequired() == null) f.setRequired(0);
            if (f.getSensitive() == null) f.setSensitive(0);
            if (f.getIsMapKey() == null) f.setIsMapKey(0);
            f.setCreatedAt(now);
            f.setUpdatedAt(now);
            typeFieldRepo.save(f);
        }
    }

    // =====================================================================
    // 模板定义
    // =====================================================================

    /** 模板列表（带一层字段），kind 为空返回全部 */
    public List<Map<String, Object>> listTemplates(String kind, Integer status) {
        List<GwTplDefinition> defs;
        if (kind != null && !kind.isBlank()) {
            defs = status != null
                    ? tplRepo.findByKindAndStatusOrderBySortNum(kind, status)
                    : tplRepo.findByKindOrderBySortNum(kind);
        } else {
            defs = tplRepo.findAllByOrderByKindAscSortNumAsc();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (GwTplDefinition d : defs) {
            Map<String, Object> m = defMap(d);
            m.put("fields", tplFieldsOneLevel(d.getId()));
            out.add(m);
        }
        return out;
    }

    /** 模板详情树：定义 + 字段 + 字段引用类型的完整递归结构（供表单渲染与校验） */
    public Map<String, Object> templateTree(Long id) {
        GwTplDefinition d = mustTpl(id);
        Map<String, Object> m = defMap(d);
        m.put("fields", resolveFieldSlots(tplFieldRepo.findByTplIdOrderBySortNum(d.getId())));
        return m;
    }

    @Transactional
    public GwTplDefinition createTemplate(GwTplSaveRequest req) {
        GwTplDefinition def = req.getDef();
        validateTemplateHeader(def, null);
        List<GwTplField> fields = normalizeFields(req.getFields());
        validateTemplateFields(def, fields);
        GwTplDefinition d = new GwTplDefinition();
        copyDef(def, d);
        d.setId(null);
        d.setBuiltin(0);
        if (d.getStatus() == null) d.setStatus(1);
        if (d.getSortNum() == null) d.setSortNum(0);
        LocalDateTime now = LocalDateTime.now();
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        d = tplRepo.save(d);
        saveFieldsOf(d.getId(), fields);
        return d;
    }

    @Transactional
    public GwTplDefinition updateTemplate(Long id, GwTplSaveRequest req) {
        GwTplDefinition d = mustTpl(id);
        if (isBuiltin(d)) throw new IllegalArgumentException("内置模板只读，请在列表中「复制」后编辑");
        validateTemplateHeader(req.getDef(), id);
        List<GwTplField> fields = normalizeFields(req.getFields());
        validateTemplateFields(d, fields);
        copyDef(req.getDef(), d);
        d.setUpdatedAt(LocalDateTime.now());
        d = tplRepo.save(d);
        saveFieldsOf(d.getId(), fields);
        return d;
    }

    @Transactional
    public void deleteTemplate(Long id) {
        GwTplDefinition d = mustTpl(id);
        if (isBuiltin(d)) throw new IllegalArgumentException("内置模板由系统维护，不允许删除");
        if (KIND_STRUCT.equals(d.getKind())) {
            for (GwConfigStruct s : structRepo.findByDelFlagOrderBySortNum(0)) {
                if (d.getName().equals(s.getStructName())) {
                    throw new IllegalArgumentException("模板正被结构化实例「" + s.getInstanceName() + "」使用，无法删除");
                }
            }
        }
        tplFieldRepo.deleteByTplId(id);
        tplRepo.deleteById(id);
    }

    // =====================================================================
    // STRUCT 实例
    // =====================================================================

    public List<GwConfigStruct> listStructs(String profile) {
        return structRepo.findByProfileCodeAndDelFlagOrderBySortNum(
                profile == null || profile.isBlank() ? "dev" : profile, 0);
    }

    @Transactional
    public GwConfigStruct createStruct(GwConfigStruct req) {
        if (!StringUtils.hasText(req.getStructName())) throw new IllegalArgumentException("结构化模板不能为空");
        GwTplDefinition tpl = tplRepo.findByKindAndName(KIND_STRUCT, req.getStructName())
                .orElseThrow(() -> new IllegalArgumentException("结构化模板不存在: " + req.getStructName()));
        List<String> errors = validateJson(templateTree(tpl.getId()), req.getValueJson());
        if (!errors.isEmpty()) throw new IllegalArgumentException("配置校验失败: " + String.join("; ", errors));
        GwConfigStruct s = new GwConfigStruct();
        copyStruct(req, s);
        s.setId(null);
        if (s.getProfileCode() == null || s.getProfileCode().isBlank()) s.setProfileCode("dev");
        if (s.getStatus() == null) s.setStatus(1);
        if (s.getDelFlag() == null) s.setDelFlag(0);
        if (s.getSortNum() == null) s.setSortNum(0);
        LocalDateTime now = LocalDateTime.now();
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        return structRepo.save(s);
    }

    @Transactional
    public GwConfigStruct updateStruct(Long id, GwConfigStruct req) {
        GwConfigStruct s = structRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("结构化实例不存在: " + id));
        String structName = StringUtils.hasText(req.getStructName()) ? req.getStructName() : s.getStructName();
        GwTplDefinition tpl = tplRepo.findByKindAndName(KIND_STRUCT, structName)
                .orElseThrow(() -> new IllegalArgumentException("结构化模板不存在: " + structName));
        List<String> errors = validateJson(templateTree(tpl.getId()), req.getValueJson());
        if (!errors.isEmpty()) throw new IllegalArgumentException("配置校验失败: " + String.join("; ", errors));
        copyStruct(req, s);
        s.setUpdatedAt(LocalDateTime.now());
        return structRepo.save(s);
    }

    @Transactional
    public void deleteStruct(Long id) {
        GwConfigStruct s = structRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("结构化实例不存在: " + id));
        s.setDelFlag(1);
        s.setUpdatedAt(LocalDateTime.now());
        structRepo.save(s);
    }

    // =====================================================================
    // 校验与导出预览
    // =====================================================================

    /**
     * 校验实例 JSON 并返回导出预览。
     *
     * @param kind      FILTER / PREDICATE / STRUCT
     * @param name      模板名（工厂短名或 STRUCT 逻辑名）
     * @param valueJson 树形实例 JSON（args 或 struct 值）
     * @return { ok, errors[], args?|properties[] }
     */
    public Map<String, Object> validateInstance(String kind, String name, String valueJson) {
        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("模板名不能为空");
        GwTplDefinition tpl = tplRepo.findByKindAndName(kind, name)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + kind + "/" + name));
        Map<String, Object> tree = templateTree(tpl.getId());
        List<String> errors = validateJson(tree, valueJson);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", errors.isEmpty());
        out.put("errors", errors);
        if (KIND_STRUCT.equals(kind)) {
            out.put("properties", flattenStruct(tree, valueJson));
        } else {
            out.put("args", flattenArgs(tree, valueJson));
        }
        return out;
    }

    /** 结构化实例导出预览 */
    public List<String> structPreview(Long id) {
        GwConfigStruct s = structRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("结构化实例不存在: " + id));
        GwTplDefinition tpl = tplRepo.findByKindAndName(KIND_STRUCT, s.getStructName())
                .orElseThrow(() -> new IllegalArgumentException("结构化模板不存在: " + s.getStructName()));
        Map<String, Object> tree = templateTree(tpl.getId());
        List<String> errors = validateJson(tree, s.getValueJson());
        if (!errors.isEmpty()) {
            return Arrays.asList("# 校验失败: " + String.join("; ", errors));
        }
        return flattenStruct(tree, s.getValueJson());
    }

    // =====================================================================
    // 内部：类型/模板校验
    // =====================================================================

    private void validateTypeHeader(GwValueType req, Long selfId) {
        if (req == null) throw new IllegalArgumentException("类型信息不能为空");
        if (!StringUtils.hasText(req.getName())) throw new IllegalArgumentException("类型名不能为空");
        if (!Set.of(KIND_BASIC, KIND_LIST, KIND_OBJECT).contains(req.getKind()))
            throw new IllegalArgumentException("kind 仅支持 BASIC/LIST/OBJECT");
        if (!StringUtils.hasText(req.getDisplayName())) req.setDisplayName(req.getName());
        long dup = selfId == null
                ? valueTypeRepo.countByName(req.getName())
                : valueTypeRepo.findAllByOrderByKindAscSortNumAsc().stream()
                        .filter(t -> t.getName().equals(req.getName()) && !t.getId().equals(selfId)).count();
        if (dup > 0) throw new IllegalArgumentException("类型名已存在: " + req.getName());
    }

    private void validateTypeFields(GwValueType type, List<GwValueTypeField> fields) {
        if (KIND_LIST.equals(type.getKind())) {
            if (fields == null || fields.isEmpty())
                throw new IllegalArgumentException("LIST 类型必须定义 1 个元素类型（role=ITEM）");
            GwValueTypeField item = fields.get(0);
            if (!ROLE_ITEM.equals(item.getRole())) item.setRole(ROLE_ITEM);
            if (item.getValueTypeId() == null) throw new IllegalArgumentException("元素类型未指定值类型");
            if (fields.size() > 1) throw new IllegalArgumentException("LIST 类型只允许 1 个 ITEM 定义");
        } else {
            if (fields == null || fields.isEmpty())
                throw new IllegalArgumentException("OBJECT 类型必须至少定义 1 个字段");
            Set<String> keys = new LinkedHashSet<>();
            for (GwValueTypeField f : fields) {
                if (!ROLE_FIELD.equals(f.getRole())) f.setRole(ROLE_FIELD);
                if (!StringUtils.hasText(f.getKeyName())) throw new IllegalArgumentException("字段 key 不能为空");
                if (!keys.add(f.getKeyName())) throw new IllegalArgumentException("字段重复: " + f.getKeyName());
                if (f.getValueTypeId() == null) throw new IllegalArgumentException("字段 " + f.getKeyName() + " 未指定值类型");
            }
        }
    }

    private void validateTemplateHeader(GwTplDefinition req, Long selfId) {
        if (req == null) throw new IllegalArgumentException("模板信息不能为空");
        if (!StringUtils.hasText(req.getName())) throw new IllegalArgumentException("模板名不能为空");
        if (!StringUtils.hasText(req.getKind())) throw new IllegalArgumentException("kind 不能为空");
        String kind = req.getKind().toUpperCase();
        req.setKind(kind);
        if (!Set.of(KIND_FILTER, KIND_PREDICATE, KIND_STRUCT).contains(kind))
            throw new IllegalArgumentException("kind 仅支持 FILTER/PREDICATE/STRUCT");
        String scope = req.getScope() == null ? "" : req.getScope().toUpperCase();
        if (KIND_STRUCT.equals(kind)) {
            if (!KIND_GLOBAL_SCOPE.equals(scope)) req.setScope(KIND_GLOBAL_SCOPE);
        } else if (KIND_PREDICATE.equals(kind)) {
            if (!KIND_ROUTE_SCOPE.equals(scope)) req.setScope(KIND_ROUTE_SCOPE);
        } else {
            if (!Set.of(KIND_ROUTE_SCOPE, KIND_GLOBAL_SCOPE, KIND_BOTH_SCOPE).contains(scope))
                req.setScope(KIND_ROUTE_SCOPE);
        }
        if (!StringUtils.hasText(req.getDisplayName())) req.setDisplayName(req.getName());
        long dup = selfId == null
                ? tplRepo.countByKindAndName(kind, req.getName())
                : tplRepo.countByKindAndNameAndIdNot(kind, req.getName(), selfId);
        if (dup > 0) throw new IllegalArgumentException("同 kind 下模板名已存在: " + req.getName());
    }

    private void validateTemplateFields(GwTplDefinition def, List<GwTplField> fields) {
        Set<String> keys = new LinkedHashSet<>();
        for (GwTplField f : fields) {
            if (!StringUtils.hasText(f.getKeyName())) throw new IllegalArgumentException("配置项 key 不能为空");
            if (!keys.add(f.getKeyName())) throw new IllegalArgumentException("配置项重复: " + f.getKeyName());
            if (f.getValueTypeId() == null) throw new IllegalArgumentException("配置项 " + f.getKeyName() + " 未指定值类型");
            mustType(f.getValueTypeId());
        }
    }

    private List<GwTplField> normalizeFields(List<GwTplField> fields) {
        return fields == null ? new ArrayList<>() : fields;
    }

    private void saveFieldsOf(Long tplId, List<GwTplField> fields) {
        LocalDateTime now = LocalDateTime.now();
        tplFieldRepo.deleteByTplId(tplId);
        int sort = 0;
        for (GwTplField f : fields) {
            f.setId(null);
            f.setTplId(tplId);
            if (f.getSortNum() == null) f.setSortNum(sort++);
            if (f.getRequired() == null) f.setRequired(0);
            if (f.getSensitive() == null) f.setSensitive(0);
            if (f.getIsMapKey() == null) f.setIsMapKey(0);
            f.setCreatedAt(now);
            f.setUpdatedAt(now);
            tplFieldRepo.save(f);
        }
    }

    // =====================================================================
    // 内部：树形结构与递归校验
    // =====================================================================

    private List<Map<String, Object>> typeFieldsOneLevel(Long typeId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GwValueTypeField f : typeFieldRepo.findByTypeIdOrderBySortNum(typeId)) {
            Map<String, Object> m = typeFieldMap(f);
            Optional<GwValueType> vt = valueTypeRepo.findById(f.getValueTypeId());
            vt.ifPresent(v -> {
                m.put("valueTypeName", v.getName());
                m.put("valueTypeKind", v.getKind());
            });
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> tplFieldsOneLevel(Long tplId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GwTplField f : tplFieldRepo.findByTplIdOrderBySortNum(tplId)) {
            Map<String, Object> m = tplFieldMap(f);
            Optional<GwValueType> vt = valueTypeRepo.findById(f.getValueTypeId());
            vt.ifPresent(v -> {
                m.put("valueTypeName", v.getName());
                m.put("valueTypeKind", v.getKind());
            });
            out.add(m);
        }
        return out;
    }

    /** 递归解析类型树：type -> {type 字段, fields:[{...字段, valueType: 子树}]} */
    private Map<String, Object> resolveTypeTree(Long typeId) {
        GwValueType t = mustType(typeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("displayName", t.getDisplayName());
        m.put("kind", t.getKind());
        m.put("fields", new ArrayList<Map<String, Object>>());
        List<Map<String, Object>> slots = new ArrayList<>();
        for (GwValueTypeField f : typeFieldRepo.findByTypeIdOrderBySortNum(typeId)) {
            Map<String, Object> slot = typeFieldMap(f);
            slot.put("valueType", resolveTypeTree(f.getValueTypeId()));
            slots.add(slot);
        }
        m.put("fields", slots);
        return m;
    }

    /** 模板字段槽：字段 + 引用类型的完整子树 */
    private List<Map<String, Object>> resolveFieldSlots(List<GwTplField> fields) {
        List<Map<String, Object>> slots = new ArrayList<>();
        for (GwTplField f : fields) {
            Map<String, Object> slot = tplFieldMap(f);
            slot.put("valueType", resolveTypeTree(f.getValueTypeId()));
            slots.add(slot);
        }
        return slots;
    }

    private List<String> validateJson(Map<String, Object> tree, String valueJson) {
        List<String> errors = new ArrayList<>();
        Object value;
        try {
            value = om.readValue(valueJson == null || valueJson.isBlank() ? "{}" : valueJson,
                    new TypeReference<Object>() {});
        } catch (Exception e) {
            errors.add("实例 JSON 非法: " + e.getMessage());
            return errors;
        }
        if (!(value instanceof Map)) {
            errors.add("实例 JSON 必须为对象");
            return errors;
        }
        Map<?, ?> m = (Map<?, ?>) value;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) tree.get("fields");
        for (Map<String, Object> slot : slots) {
            String key = str(slot.get("keyName"));
            Object v = m.get(key);
            validateSlot(slot, v, key, errors);
        }
        return errors;
    }

    /** 递归校验单个槽值（含 map/list/object/basic + 正则/枚举） */
    private void validateSlot(Map<String, Object> slot, Object value, String path, List<String> errors) {
        if (slot == null) return;
        String label = slot.get("label") != null && StringUtils.hasText(str(slot.get("label")))
                ? str(slot.get("label")) : str(slot.get("keyName"));
        if (value == null) {
            if (intVal(slot.get("required")) == 1) errors.add(path + "（" + label + "）必填");
            return;
        }
        if (intVal(slot.get("isMapKey")) == 1) {
            if (!(value instanceof Map)) {
                errors.add(path + "（" + label + "）应为 map");
                return;
            }
            Map<?, ?> m = (Map<?, ?>) value;
            List<String> keys = strList(slot.get("optionsJson"));
            String mkValidator = str(slot.get("mapKeyValidator"));
            Map<String, Object> vt = asMap(slot.get("valueType"));
            for (Object ko : m.keySet()) {
                String k = String.valueOf(ko);
                String child = path + "['" + k + "']";
                if (keys != null && !keys.isEmpty() && !keys.contains(k)) {
                    errors.add(child + " 不在允许的键集合中");
                } else if (StringUtils.hasText(mkValidator) && !k.matches(mkValidator)) {
                    errors.add(child + " 键不满足规则（正则: " + mkValidator + "）");
                }
                validateSlot(vt, m.get(ko), child, errors);
            }
            return;
        }
        Map<String, Object> vt = asMap(slot.get("valueType"));
        if (vt == null) {
            errors.add(path + " 值类型缺失");
            return;
        }
        String kind = str(vt.get("kind"));
        if (KIND_LIST.equals(kind)) {
            if (!(value instanceof List)) {
                errors.add(path + "（" + label + "）应为列表");
                return;
            }
            List<?> l = (List<?>) value;
            Map<String, Object> itemSlot = firstItemSlot(vt);
            for (int i = 0; i < l.size(); i++) {
                validateSlot(itemSlot, l.get(i), path + "[" + i + "]", errors);
            }
            return;
        }
        if (KIND_OBJECT.equals(kind)) {
            if (!(value instanceof Map)) {
                errors.add(path + "（" + label + "）应为对象");
                return;
            }
            Map<?, ?> m = (Map<?, ?>) value;
            for (Map<String, Object> f : fieldSlotsOf(vt)) {
                String fk = str(f.get("keyName"));
                validateSlot(f, m.get(fk), path + "." + fk, errors);
            }
            return;
        }
        // BASIC
        String typeName = str(vt.get("name"));
        validateBasic(typeName, slot, value, path, errors);
        String validator = str(slot.get("validator"));
        if (StringUtils.hasText(validator) && !String.valueOf(value).matches(validator)) {
            errors.add(path + "（" + label + "）不满足校验规则: " + validator);
        }
    }

    private void validateBasic(String typeName, Map<String, Object> slot, Object value, String path,
                               List<String> errors) {
        switch (typeName) {
            case "INT", "LONG" -> {
                if (value instanceof Number) return;
                if (value instanceof String s && s.matches("-?\\d+")) return;
                errors.add(path + " 应为整数");
            }
            case "DOUBLE" -> {
                if (value instanceof Number) return;
                if (value instanceof String s && s.matches("-?\\d+(\\.\\d+)?")) return;
                errors.add(path + " 应为数值");
            }
            case "BOOL" -> {
                if (value instanceof Boolean) return;
                if (value instanceof String s && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"))) return;
                errors.add(path + " 应为布尔值");
            }
            case "ENUM" -> {
                List<String> opts = strList(slot.get("optionsJson"));
                if (opts != null && !opts.isEmpty() && !opts.contains(String.valueOf(value))) {
                    errors.add(path + " 取值必须在 " + opts + " 中");
                }
            }
            default -> {
                // STRING / DURATION / SIZE / DATETIME / TEXTAREA / SENSITIVE：接受任意标量字符串化
                if (value instanceof Map || value instanceof List) errors.add(path + " 应为标量值");
            }
        }
    }

    // =====================================================================
    // 内部：导出预览
    // =====================================================================

    /**
     * FILTER/PREDICATE：args 扁平键。列表逗号拼接；嵌套对象点号展开；map 标量键点号展开。
     * 返回 LinkedHashMap<导出键, 值字符串>。
     */
    private Map<String, String> flattenArgs(Map<String, Object> tree, String valueJson) {
        Map<String, String> out = new LinkedHashMap<>();
        Object value;
        try {
            value = om.readValue(valueJson == null || valueJson.isBlank() ? "{}" : valueJson,
                    new TypeReference<Object>() {});
        } catch (Exception e) {
            return out;
        }
        if (!(value instanceof Map)) return out;
        Map<?, ?> m = (Map<?, ?>) value;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) tree.get("fields");
        for (Map<String, Object> slot : slots) {
            String key = str(slot.get("keyName"));
            if (m.containsKey(key)) {
                flattenArgsSlot("args." + exportKeyOf(slot), slot, m.get(key), out);
            }
        }
        return out;
    }

    private void flattenArgsSlot(String path, Map<String, Object> slot, Object value, Map<String, String> out) {
        if (value == null) return;
        if (intVal(slot.get("isMapKey")) == 1) {
            if (!(value instanceof Map)) return;
            Map<?, ?> m = (Map<?, ?>) value;
            Map<String, Object> vt = asMap(slot.get("valueType"));
            for (Object ko : m.keySet()) {
                flattenArgsSlot(path + "." + ko, vt, m.get(ko), out);
            }
            return;
        }
        Map<String, Object> vt = asMap(slot.get("valueType"));
        if (vt == null) return;
        String kind = str(vt.get("kind"));
        if (KIND_LIST.equals(kind)) {
            if (value instanceof List l) {
                List<String> items = new ArrayList<>();
                for (Object o : l) items.add(String.valueOf(o));
                out.put(path, String.join(",", items));
            }
            return;
        }
        if (KIND_OBJECT.equals(kind)) {
            if (!(value instanceof Map)) return;
            Map<?, ?> m = (Map<?, ?>) value;
            for (Map<String, Object> f : fieldSlotsOf(vt)) {
                String fk = str(f.get("keyName"));
                if (m.containsKey(fk)) flattenArgsSlot(path + "." + exportKeyOf(f), f, m.get(fk), out);
            }
            return;
        }
        out.put(path, String.valueOf(value));
    }

    /** STRUCT：完整 properties 行。base = spring.cloud.gateway.server.webflux.<structName> */
    private List<String> flattenStruct(Map<String, Object> tree, String valueJson) {
        List<String> lines = new ArrayList<>();
        Object value;
        try {
            value = om.readValue(valueJson == null || valueJson.isBlank() ? "{}" : valueJson,
                    new TypeReference<Object>() {});
        } catch (Exception e) {
            return List.of("# 实例 JSON 非法: " + e.getMessage());
        }
        if (!(value instanceof Map)) return List.of("# 实例 JSON 必须为对象");
        Map<?, ?> m = (Map<?, ?>) value;
        String structName = str(tree.get("name"));
        String base = "spring.cloud.gateway.server.webflux." + structName;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) tree.get("fields");
        for (Map<String, Object> slot : slots) {
            String key = str(slot.get("keyName"));
            if (m.containsKey(key)) {
                flattenStructSlot(base + "." + exportKeyOf(slot), slot, m.get(key), lines);
            }
        }
        return lines;
    }

    private void flattenStructSlot(String path, Map<String, Object> slot, Object value, List<String> lines) {
        if (value == null) return;
        if (intVal(slot.get("isMapKey")) == 1) {
            if (!(value instanceof Map)) return;
            Map<?, ?> m = (Map<?, ?>) value;
            Map<String, Object> vt = asMap(slot.get("valueType"));
            for (Object ko : m.keySet()) {
                flattenStructSlot(path + "['" + ko + "']", vt, m.get(ko), lines);
            }
            return;
        }
        Map<String, Object> vt = asMap(slot.get("valueType"));
        if (vt == null) return;
        String kind = str(vt.get("kind"));
        if (KIND_LIST.equals(kind)) {
            if (value instanceof List l) {
                Map<String, Object> item = firstItemSlot(vt);
                for (int i = 0; i < l.size(); i++) {
                    flattenStructSlot(path + "[" + i + "]", item, l.get(i), lines);
                }
            }
            return;
        }
        if (KIND_OBJECT.equals(kind)) {
            if (!(value instanceof Map)) return;
            Map<?, ?> m = (Map<?, ?>) value;
            for (Map<String, Object> f : fieldSlotsOf(vt)) {
                String fk = str(f.get("keyName"));
                if (m.containsKey(fk)) flattenStructSlot(path + "." + exportKeyOf(f), f, m.get(fk), lines);
            }
            return;
        }
        lines.add(path + "=" + String.valueOf(value));
    }

    // =====================================================================
    // 内部：辅助
    // =====================================================================

    private static final String KIND_GLOBAL_SCOPE = "GLOBAL";
    private static final String KIND_ROUTE_SCOPE = "ROUTE";
    private static final String KIND_BOTH_SCOPE = "BOTH";

    private GwValueType mustType(Long id) {
        return valueTypeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("值类型不存在: " + id));
    }

    private GwTplDefinition mustTpl(Long id) {
        return tplRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + id));
    }

    private boolean isBuiltin(GwValueType t) {
        return t.getBuiltin() != null && t.getBuiltin() == 1;
    }

    private boolean isBuiltin(GwTplDefinition d) {
        return d.getBuiltin() != null && d.getBuiltin() == 1;
    }

    /** 类型被哪些模板/类型引用（返回引用者描述，无引用返回 null） */
    private String firstTypeReference(Long typeId) {
        for (GwTplDefinition d : tplRepo.findAll()) {
            for (GwTplField f : tplFieldRepo.findByTplIdOrderBySortNum(d.getId())) {
                if (typeId.equals(f.getValueTypeId())) return "模板 " + d.getDisplayName();
            }
        }
        for (GwValueType t : valueTypeRepo.findAll()) {
            for (GwValueTypeField f : typeFieldRepo.findByTypeIdOrderBySortNum(t.getId())) {
                if (typeId.equals(f.getValueTypeId())) return "类型 " + t.getDisplayName();
            }
        }
        return null;
    }

    private Map<String, Object> defMap(GwTplDefinition d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("kind", d.getKind());
        m.put("name", d.getName());
        m.put("displayName", d.getDisplayName());
        m.put("category", d.getCategory());
        m.put("scope", d.getScope());
        m.put("singletonPerRoute", d.getSingletonPerRoute());
        m.put("builtin", d.getBuiltin());
        m.put("sortNum", d.getSortNum());
        m.put("status", d.getStatus());
        m.put("remark", d.getRemark());
        return m;
    }

    private Map<String, Object> tplFieldMap(GwTplField f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("keyName", f.getKeyName());
        m.put("label", f.getLabel());
        m.put("valueTypeId", f.getValueTypeId());
        m.put("required", f.getRequired());
        m.put("defaultValue", f.getDefaultValue());
        m.put("optionsJson", f.getOptionsJson());
        m.put("placeholder", f.getPlaceholder());
        m.put("validator", f.getValidator());
        m.put("sensitive", f.getSensitive());
        m.put("isMapKey", f.getIsMapKey());
        m.put("mapKeyValidator", f.getMapKeyValidator());
        m.put("exportKey", f.getExportKey());
        m.put("sortNum", f.getSortNum());
        m.put("remark", f.getRemark());
        return m;
    }

    private Map<String, Object> typeFieldMap(GwValueTypeField f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("role", f.getRole());
        m.put("keyName", f.getKeyName());
        m.put("label", f.getLabel());
        m.put("valueTypeId", f.getValueTypeId());
        m.put("required", f.getRequired());
        m.put("defaultValue", f.getDefaultValue());
        m.put("optionsJson", f.getOptionsJson());
        m.put("placeholder", f.getPlaceholder());
        m.put("validator", f.getValidator());
        m.put("sensitive", f.getSensitive());
        m.put("isMapKey", f.getIsMapKey());
        m.put("mapKeyValidator", f.getMapKeyValidator());
        m.put("exportKey", f.getExportKey());
        m.put("sortNum", f.getSortNum());
        m.put("remark", f.getRemark());
        return m;
    }

    private void copyType(GwValueType src, GwValueType dst) {
        dst.setName(src.getName());
        dst.setDisplayName(src.getDisplayName());
        dst.setKind(src.getKind());
        dst.setSortNum(src.getSortNum());
        dst.setStatus(src.getStatus());
        dst.setRemark(src.getRemark());
    }

    private void copyDef(GwTplDefinition src, GwTplDefinition dst) {
        dst.setKind(src.getKind());
        dst.setName(src.getName());
        dst.setDisplayName(src.getDisplayName());
        dst.setCategory(src.getCategory());
        dst.setScope(src.getScope());
        dst.setSingletonPerRoute(src.getSingletonPerRoute());
        dst.setSortNum(src.getSortNum());
        dst.setStatus(src.getStatus());
        dst.setRemark(src.getRemark());
    }

    private void copyStruct(GwConfigStruct src, GwConfigStruct dst) {
        dst.setProfileCode(src.getProfileCode());
        dst.setStructName(src.getStructName());
        dst.setInstanceName(src.getInstanceName());
        dst.setValueJson(src.getValueJson());
        dst.setSortNum(src.getSortNum());
        dst.setStatus(src.getStatus());
        dst.setRemark(src.getRemark());
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** 导出键：优先 export_key，缺省回退 key_name（cors-configurations / allowed-origins 等 kebab-case） */
    private static String exportKeyOf(Map<String, Object> slot) {
        String k = str(slot.get("exportKey"));
        return StringUtils.hasText(k) ? k : str(slot.get("keyName"));
    }

    private static int intVal(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(str(o));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    /** LIST 类型的 ITEM 槽（无则返回空槽） */
    private static Map<String, Object> firstItemSlot(Map<String, Object> vt) {
        List<Map<String, Object>> fields = asMaps(vt.get("fields"));
        for (Map<String, Object> f : fields) {
            if (ROLE_ITEM.equals(str(f.get("role")))) return f;
        }
        return new LinkedHashMap<>();
    }

    private static List<Map<String, Object>> fieldSlotsOf(Map<String, Object> vt) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : asMaps(vt.get("fields"))) {
            if (ROLE_FIELD.equals(str(f.get("role")))) out.add(f);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMaps(Object o) {
        if (o instanceof List<?> l) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object x : l) {
                if (x instanceof Map) out.add((Map<String, Object>) x);
            }
            return out;
        }
        return new ArrayList<>();
    }

    /** optionsJson（数组 JSON）→ 字符串列表 */
    private static List<String> strList(Object optionsJson) {
        if (optionsJson == null) return null;
        String s = str(optionsJson).trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("[") && s.endsWith("]")) {
            try {
                return om.readValue(s, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                // 落到逗号拆分
            }
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }
}
