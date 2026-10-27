package xyz.wewin.autumn.gateway.dashboard.entity.gateway;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 过滤器模板，对应表 gw_filter_template。
 * args_schema_json 是参数表单定义数组，前端据此动态渲染。
 */
@Table("gw_filter_template")
public class GwFilterTemplate {
    @Id
    private Long id;
    @Column("filter_name")
    private String filterName;
    @Column("display_name")
    private String displayName;
    private String scope;     // ROUTE/GLOBAL/BOTH
    @Column("singleton_per_route")
    private Integer singletonPerRoute;
    @Column("args_schema_json")
    private String argsSchemaJson;
    private Integer status;
    private String remark;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFilterName() { return filterName; }
    public void setFilterName(String filterName) { this.filterName = filterName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Integer getSingletonPerRoute() { return singletonPerRoute; }
    public void setSingletonPerRoute(Integer singletonPerRoute) { this.singletonPerRoute = singletonPerRoute; }
    public String getArgsSchemaJson() { return argsSchemaJson; }
    public void setArgsSchemaJson(String argsSchemaJson) { this.argsSchemaJson = argsSchemaJson; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
