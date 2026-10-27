package xyz.wewin.autumn.gateway.dashboard.entity.gateway;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 断言模板，对应表 gw_predicate_template。
 */
@Table("gw_predicate_template")
public class GwPredicateTemplate {
    @Id
    private Long id;
    @Column("predicate_name")
    private String predicateName;
    @Column("display_name")
    private String displayName;
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
    public String getPredicateName() { return predicateName; }
    public void setPredicateName(String predicateName) { this.predicateName = predicateName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
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
