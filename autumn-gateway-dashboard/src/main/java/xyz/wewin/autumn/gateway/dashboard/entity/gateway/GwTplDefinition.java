package xyz.wewin.autumn.gateway.dashboard.entity.gateway;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 模板定义（v2），对应表 gw_tpl_definition。
 * kind=FILTER/PREDICATE/STRUCT；builtin=1 为内置模板（前端只读，编辑=复制）。
 */
@Table("gw_tpl_definition")
public class GwTplDefinition {
    @Id
    private Long id;
    private String kind;      // FILTER / PREDICATE / STRUCT
    private String name;      // 工厂短名（FILTER/PREDICATE）或逻辑名（STRUCT）
    @Column("display_name")
    private String displayName;
    private String category;  // 选择器分组
    private String scope;     // ROUTE / GLOBAL / BOTH（STRUCT 恒 GLOBAL）
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
