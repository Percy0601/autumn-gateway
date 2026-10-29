package xyz.wewin.autumn.gateway.dashboard.entity.gateway;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 模板定义（v2），对应表 gw_tpl_definition。
 * kind=FILTER/PREDICATE/STRUCT/CONFIG；builtin=1 为内置模板（前端只读，编辑=复制）。
 * config_group 仅 CONFIG（全局 KV 配置组）模板使用：该组配置落 gw_config_item.config_group 的值。
 */
@Table("gw_tpl_definition")
public class GwTplDefinition {
    @Id
    private Long id;
    private String kind;      // FILTER / PREDICATE / STRUCT / CONFIG
    private String name;      // 工厂短名（FILTER/PREDICATE）、逻辑名（STRUCT）或配置组前缀（CONFIG）
    @Column("display_name")
    private String displayName;
    private String category;  // 选择器分组
    private String scope;     // ROUTE / GLOBAL / BOTH（STRUCT/CONFIG 恒 GLOBAL）
    @Column("config_group")
    private String configGroup; // 仅 CONFIG：落 gw_config_item.config_group 的值（如 AUTHN/SCG）
    @Column("singleton_per_route")
    private Integer singletonPerRoute;
    private Integer builtin;
    @Column("sort_num")
    private Integer sortNum;
    private Integer status;
    private String remark;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getConfigGroup() { return configGroup; }
    public void setConfigGroup(String configGroup) { this.configGroup = configGroup; }
    public Integer getSingletonPerRoute() { return singletonPerRoute; }
    public void setSingletonPerRoute(Integer singletonPerRoute) { this.singletonPerRoute = singletonPerRoute; }
    public Integer getBuiltin() { return builtin; }
    public void setBuiltin(Integer builtin) { this.builtin = builtin; }
    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
