package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 命名空间（租户），对应表 basic_namespace。
 * 一个命名空间相当于一个公司/企业，其下可挂载多个应用（basic_app）。
 */
@Table("basic_namespace")
public class BasicNamespace {
    @Id
    private Long id;
    @Column("code")
    private String code; // 命名空间编码，如 default
    @Column("name")
    private String name; // 命名空间名称，如 默认租户 / 某公司
    @Column("description")
    private String description;
    @Column("status")
    private Integer status; // 1=正常 0=禁用
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
