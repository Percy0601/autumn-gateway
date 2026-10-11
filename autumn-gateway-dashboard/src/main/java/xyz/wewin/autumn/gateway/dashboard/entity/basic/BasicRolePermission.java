package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 角色权限关联表，对应表 basic_role_permission
 */
@Table("basic_role_permission")
public class BasicRolePermission {
    @Id
    private Long id;
    @Column("role_id")
    private Long roleId; // 角色Id
    @Column("ref_id")
    private Long refId; // data_scope_id/menu_id
    @Column("create_time")
    private LocalDateTime createTime; // 创建时间
    @Column("update_time")
    private LocalDateTime updateTime; // 更新时间
    @Column("del_flag")
    private Integer delFlag; // 删除状态: 0:否，1：是
    @Column("op_user_id")
    private Long opUserId; // 操作人Id
    private Integer type; // 权限类型：1-菜单权限；2-资源；3-数据权限

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }

    public Long getOpUserId() { return opUserId; }
    public void setOpUserId(Long opUserId) { this.opUserId = opUserId; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
}
