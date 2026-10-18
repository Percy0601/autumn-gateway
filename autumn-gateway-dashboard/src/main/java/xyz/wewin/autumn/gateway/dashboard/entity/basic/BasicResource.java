package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 权限原子项（合并了 Resource 的 URL 四元组），对应表 basic_resource
 */
@Table("basic_resource")
public class BasicResource {
    @Id
    private Long id;
    @Column("app_id")
    private Long appId;
    @Column("app_code")
    private String appCode;
    private String name; // 权限名称，如"创建订单"
    private String path; // URL 模式，如 /api/order/:id；MENU 时为路由路径
    @Column("http_method")
    private String httpMethod; // 支持多选，逗号分隔，如 GET,POST；ALL 表示全部方法
    @Column("match_type")
    private String matchType; // exact / prefix / suffix
    private String description;
    private Integer status; // 1=正常 0=禁用
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }

    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
