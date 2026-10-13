package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 菜单与资源关联表，对应表 basic_menu_resource
 */
@Table("basic_menu_resource")
public class BasicMenuResource {
    @Id
    private Long id;
    @Column("menu_id")
    private Long menuId; // 菜单ID
    @Column("resource_id")
    private Long resourceId; // 资源ID
    private Integer deleted; // 删除状态：0-否 1-是
    @Column("create_time")
    private LocalDateTime createTime; // 创建时间
    @Column("update_time")
    private LocalDateTime updateTime; // 更新时间
    @Column("op_user_id")
    private Long opUserId; // 操作人

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMenuId() { return menuId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }

    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Long getOpUserId() { return opUserId; }
    public void setOpUserId(Long opUserId) { this.opUserId = opUserId; }
}
