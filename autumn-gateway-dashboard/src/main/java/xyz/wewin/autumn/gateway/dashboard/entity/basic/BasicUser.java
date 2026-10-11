package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 基础用户表，对应表 basic_user
 */
@Table("basic_user")
public class BasicUser {
    @Id
    private Long id;
    @Column("emp_no")
    private String empNo; // ESB人员工号
    private Integer admin; // 是否是管理员：1-是 0-否
    @Column("latest_role_id")
    private Long latestRoleId; // 最近登录使用角色Id
    @Column("create_time")
    private LocalDateTime createTime; // 创建时间
    @Column("update_time")
    private LocalDateTime updateTime; // 更新时间

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmpNo() {
        return empNo;
    }

    public void setEmpNo(String empNo) {
        this.empNo = empNo;
    }

    public Integer getAdmin() { return admin; }
    public void setAdmin(Integer admin) { this.admin = admin; }

    public Long getLatestRoleId() { return latestRoleId; }
    public void setLatestRoleId(Long latestRoleId) { this.latestRoleId = latestRoleId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
