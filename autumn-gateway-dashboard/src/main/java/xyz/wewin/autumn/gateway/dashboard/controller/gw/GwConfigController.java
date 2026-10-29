package xyz.wewin.autumn.gateway.dashboard.controller.gw;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
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
import xyz.wewin.autumn.gateway.dashboard.service.gw.GwTemplateV2Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 网关全局配置项：/api/system/gateway/config/**
 * <p>排序口径与配置同步页一致：组顺序（SERVER→DISCOVERY→AUTHN→AUTHZ→LIMIT→SCG→CORS→CUSTOM）
 * → 组内 sort_num → key，同项目配置集中连续展示。</p>
 */
@RestController
@RequestMapping("/api/system/gateway/config")
public class GwConfigController {

    private static final String[] CONFIG_GROUP_ORDER = {"SERVER", "DISCOVERY", "AUTHN", "AUTHZ", "LIMIT", "SCG", "CORS", "CUSTOM"};
    private static final ObjectMapper om = new ObjectMapper();

    @Autowired
    private GwConfigItemRepository repo;

    @Autowired
    private GwTemplateV2Service tplService;

    @GetMapping
    public Result<List<GwConfigItem>> list(@RequestParam(defaultValue = "dev") String profile) {
        List<GwConfigItem> items = new ArrayList<>(repo.findByProfile(profile));
        items.sort(Comparator.comparingInt((GwConfigItem it) -> groupOrder(it.getConfigGroup()))
                .thenComparing(GwConfigItem::getSortNum, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GwConfigItem::getConfigKey, Comparator.nullsLast(Comparator.naturalOrder())));
        return Result.success(items);
    }

    /**
     * 从 CONFIG 模板添加全局配置：body = { profileCode?, tplId, values:{keyName: value} }。
     * 服务端按模板校验（必填/类型/正则）→ 拼 config_key（模板前缀.exportKey）→ 落库，
     * sensitive 字段自动 is_encrypted=1；返回创建的配置项列表。
     */
    @PostMapping("/from-template")
    public Result<List<GwConfigItem>> createFromTemplate(@RequestBody Map<String, Object> body) {
        if (body.get("tplId") == null) return Result.error(400, "tplId 不能为空");
        Long tplId = Long.valueOf(String.valueOf(body.get("tplId")));
        String profileCode = body.get("profileCode") == null ? "dev" : String.valueOf(body.get("profileCode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) body.get("values");
        if (values == null || values.isEmpty()) return Result.error(400, "values 不能为空");

        Map<String, Object> tree = tplService.templateTree(tplId);
        if (!"CONFIG".equals(tree.get("kind"))) return Result.error(400, "仅支持 CONFIG（全局配置组）模板");
        String tplName = String.valueOf(tree.get("name"));
        Map<String, Object> validate;
        try {
            validate = tplService.validateInstance("CONFIG", tplName, om.writeValueAsString(values));
        } catch (Exception e) {
            return Result.error(400, "配置值无法解析: " + e.getMessage());
        }
        if (!Boolean.TRUE.equals(validate.get("ok"))) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) validate.get("errors");
            return Result.error(400, "校验失败: " + String.join("; ", errors));
        }
        String configGroup = tree.get("configGroup") == null || !StringUtils.hasText(String.valueOf(tree.get("configGroup")))
                ? "CUSTOM" : String.valueOf(tree.get("configGroup"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) validate.get("configRows");
        List<GwConfigItem> saved = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("key"));
            if (repo.countByKey(profileCode, key) > 0) {
                return Result.error(400, "配置项已存在（" + profileCode + "）: " + key);
            }
            GwConfigItem item = new GwConfigItem();
            item.setProfileCode(profileCode);
            item.setConfigGroup(configGroup);
            item.setConfigKey(key);
            int sensitive = row.get("sensitive") == null ? 0 : ((Number) row.get("sensitive")).intValue();
            item.setConfigValue(String.valueOf(row.get("value")));
            item.setValueType(sensitive == 1 ? "STRING" : mapValueType(String.valueOf(row.get("valueType"))));
            item.setIsRequired(row.get("required") == null ? 0 : ((Number) row.get("required")).intValue());
            item.setIsEncrypted(sensitive);
            item.setSortNum(nextSort(profileCode, configGroup));
            item.setRemark(String.valueOf(row.get("label")));
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            saved.add(repo.save(item));
        }
        return Result.success(saved);
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

    // ==================== 内部 ====================

    private int nextSort(String profileCode, String configGroup) {
        int max = 0;
        for (GwConfigItem it : repo.findByProfile(profileCode)) {
            String g = it.getConfigGroup() == null ? "CUSTOM" : it.getConfigGroup();
            if (g.equals(configGroup) && it.getSortNum() != null) {
                max = Math.max(max, it.getSortNum());
            }
        }
        return max + 1;
    }

    private static int groupOrder(String group) {
        String g = group == null ? "CUSTOM" : group;
        for (int i = 0; i < CONFIG_GROUP_ORDER.length; i++) {
            if (CONFIG_GROUP_ORDER[i].equals(g)) return i;
        }
        return CONFIG_GROUP_ORDER.length;
    }

    /** 模板值类型 → gw_config_item.value_type（SENSITIVE 已单独处理为 STRING+is_encrypted） */
    private static String mapValueType(String tplType) {
        if (tplType == null) return "STRING";
        switch (tplType) {
            case "INT": return "INT";
            case "LONG": return "LONG";
            case "BOOL": return "BOOL";
            case "DURATION": return "DURATION";
            case "TEXTAREA": return "TEXTAREA";
            case "LIST_STRING":
            case "LIST_INT":
            case "LIST_HTTP_METHOD":
            case "LIST_HTTP_SERIES":
                return "LIST";
            default: return "STRING"; // STRING/ENUM/DATETIME/SIZE/DOUBLE/SENSITIVE
        }
    }
}
