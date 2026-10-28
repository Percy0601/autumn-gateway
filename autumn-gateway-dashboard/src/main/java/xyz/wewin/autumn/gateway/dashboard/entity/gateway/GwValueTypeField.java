package xyz.wewin.autumn.gateway.dashboard.entity.gateway;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 复合类型结构（v2），对应表 gw_value_type_field。
 * role=ITEM：LIST 类型的元素定义（每个 LIST 恰 1 行，keyName 固定 ITEM）；
 * role=FIELD：OBJECT 类型的字段定义（1..N 行）。
 * valueTypeId 自引用 gw_value_type.id → 递归表达嵌套（LIST 的元素 / OBJECT 的字段可再为复合类型或 map）。
 */
@Table("gw_value_type_field")
public class GwValueTypeField {
    @Id
    private Long id;
    @Column("type_id")
    private Long typeId;
    private String role;   // ITEM / FIELD
    @Column("key_name")
    private String keyName;
    private String label;
    @Column("value_type_id")
    private Long valueTypeId;
    private Integer required;
    @Column("default_value")
    private String defaultValue;
    @Column("options_json")
    private String optionsJson;
    private String placeholder;
    private String validator;
    private Integer sensitive;
    @Column("is_map_key")
    private Integer isMapKey;
    @Column("map_key_validator")
    private String mapKeyValidator;
    @Column("export_key")
    private String exportKey;
    @Column("sort_num")
    private Integer sortNum;
    private String remark;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Long getValueTypeId() { return valueTypeId; }
    public void setValueTypeId(Long valueTypeId) { this.valueTypeId = valueTypeId; }
    public Integer getRequired() { return required; }
    public void setRequired(Integer required) { this.required = required; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }
    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
    public String getValidator() { return validator; }
    public void setValidator(String validator) { this.validator = validator; }
    public Integer getSensitive() { return sensitive; }
    public void setSensitive(Integer sensitive) { this.sensitive = sensitive; }
    public Integer getIsMapKey() { return isMapKey; }
    public void setIsMapKey(Integer isMapKey) { this.isMapKey = isMapKey; }
    public String getMapKeyValidator() { return mapKeyValidator; }
    public void setMapKeyValidator(String mapKeyValidator) { this.mapKeyValidator = mapKeyValidator; }
    public String getExportKey() { return exportKey; }
    public void setExportKey(String exportKey) { this.exportKey = exportKey; }
    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
