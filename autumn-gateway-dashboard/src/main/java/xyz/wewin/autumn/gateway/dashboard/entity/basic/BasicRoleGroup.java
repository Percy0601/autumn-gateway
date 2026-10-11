package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 用户组与角色关联表，对应表 basic_role_group
 */
@Table("basic_role_group")
public class BasicRoleGroup {
    @Id
    private Long id;
    @Column("role_id")
    private Long roleId; // 角色ID
    @Column("group_id")
    private Long groupId; // 用户组ID
    private Integer deleted; // 删除状态：0-否 1-是
    @Column("create_time")
    private LocalDateTime createTime; // 创建时间
    @Column("update_time")
    private LocalDateTime updateTime; // 更新时间
    @Column("op_user_id")
    private Long opUserId; // 操作人

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Long getOpUserId() { return opUserId; }
    public void setOpUserId(Long opUserId) { this.opUserId = opUserId; }
}
