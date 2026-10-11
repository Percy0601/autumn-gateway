package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 应用（多应用隔离），对应表 basic_app
 */
@Table("basic_app")
public class BasicApp {
    @Id
    private Long id;
    @Column("app_code")
    private String appCode; // 短标识，如 order、crm、admin
    private String name;
    @Column("base_path")
    private String basePath; // 该应用 API 统一前缀，如 /api/order
    private String description;
    private Integer status; // 1=正常 0=禁用
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
