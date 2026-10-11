package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 用户最后登录角色，解决多端登录，对应表 basic_user_access_role
 */
@Table("basic_user_access_role")
public class BasicUserAccessRole {
    @Id
    private Long id;
    @Column("user_id")
    private Long userId; // 用户Id
    @Column("latest_role_id")
    private Long latestRoleId; // 最近登录使用角色Id
    @Column("create_time")
    private LocalDateTime createTime; // 创建时间
    @Column("update_time")
    private LocalDateTime updateTime; // 更新时间
    @Column("emp_id")
    private String empId; // jwt头部32位，用于索引
    @Column("del_flag")
    private Integer delFlag; // 删除标志：0-否 1-是
    @Column("access_time")
    private Long accessTime; // 登录系统时间，即生成jwt的create_time

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getLatestRoleId() { return latestRoleId; }
    public void setLatestRoleId(Long latestRoleId) { this.latestRoleId = latestRoleId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }

    public Long getAccessTime() { return accessTime; }
    public void setAccessTime(Long accessTime) { this.accessTime = accessTime; }
}
