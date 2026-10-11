package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 基础用户关联角色表，对应表 basic_user_role
 */
@Table("basic_user_role")
public class BasicUserRole {
    @Id
    private Long id;
    @Column("app_id")
    private Long appId; // 应用ID：1-运营后台 2-POP后台 3-B2B后台 4-互联网医院后台 5-数据中台 6-销售助手 100-商家后台
    @Column("user_id")
    private Long userId; // 用户ID
    @Column("role_id")
    private Long roleId; // 角色ID
    @Column("del_flag")
    private Integer delFlag; // 是否删除：0-否 1-是
    @Column("create_user")
    private Long createUser; // 创建人id
    @Column("create_time")
    private LocalDateTime createTime; // 创建时间
    @Column("update_user")
    private Long updateUser; // 更新人id
    @Column("update_time")
    private LocalDateTime updateTime; // 更新时间
    private String remark; // 备注

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }

    public Long getCreateUser() { return createUser; }
    public void setCreateUser(Long createUser) { this.createUser = createUser; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public Long getUpdateUser() { return updateUser; }
    public void setUpdateUser(Long updateUser) { this.updateUser = updateUser; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
