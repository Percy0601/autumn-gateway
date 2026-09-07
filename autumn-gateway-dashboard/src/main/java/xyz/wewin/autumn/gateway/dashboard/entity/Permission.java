package xyz.wewin.autumn.gateway.dashboard.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("permission")
public class Permission {
    @Id
    private Long id;
    @Column("app_id")
    private Long appId;
    private String category;
    private String code;
    private String name;
    @Column("perm_type")
    private String permType; // MENU, API, BUTTON, DATA
    // 以下为合并原 resource 表的字段
    @Column("resource_path")
    private String resourcePath; // URL 模式，如 /api/order/:id
    @Column("http_method")
    private String httpMethod;   // ALL, GET, POST, PUT, DELETE, PATCH
    @Column("match_type")
    private String matchType;    // exact, prefix, suffix
    @Column("parent_id")
    private Long parentId;       // MENU 树形结构，0=顶级
    private String icon;
    private Integer sort;
    private Integer hidden;      // 0=显示 1=隐藏
    // 通用字段
    private String description;
    private Integer status;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    // ---- getters/setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPermType() { return permType; }
    public void setPermType(String permType) { this.permType = permType; }

    public String getResourcePath() { return resourcePath; }
    public void setResourcePath(String resourcePath) { this.resourcePath = resourcePath; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public Integer getHidden() { return hidden; }
    public void setHidden(Integer hidden) { this.hidden = hidden; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
