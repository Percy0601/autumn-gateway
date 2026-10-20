package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 基础用户表，对应表 basic_user。
 *
 * <p>本表已整合旧 user 主表的字段：uuid / username / nickname / avatar / email / phone /
 * status / password_updated_at / last_login_at，旧 user 表不再使用。
 *
 * <p>命名沿用 basic_* 规范，时间字段为 create_time / update_time
 * （对应旧表的 created_at / updated_at）。
 *
 * <p>约定：
 * <ul>
 *   <li>uuid、username 全局唯一，创建后不再变更；username 为空表示尚未设置登录名。</li>
 *   <li>status：1=正常，0=禁用（与旧 user 表一致）。</li>
 *   <li>密码等凭据不在此表，仍由 basic_user_auth 之类的认证账户表承载。</li>
 * </ul>
 */
@Table("basic_user")
public class BasicUser {
    @Id
    private Long id; // 主键ID，自增

    @Column("uuid")
    private String uuid; // 对外统一标识（OIDC 的 sub）：生成后永不变化，避免暴露自增ID、便于跨库合并；唯一，可为 NULL

    private String username; // 登录名（工号语义）：唯一，创建后禁止修改，如需变更只能后台改库

    private String nickname; // 昵称/姓名

    private String avatar; // 头像地址

    private String email; // 邮箱

    private String phone; // 手机号

    @Column("emp_no")
    private String empNo; // ESB人员工号（与登录名分列，登录名是系统账号，工号是人事工号）

    private Integer admin; // 是否是管理员：1-是；0-否

    private Integer status; // 状态：1-正常；0-禁用

    @Column("latest_role_id")
    private Long latestRoleId; // 最近登录使用的角色Id

    @Column("password")
    private String password; // 登录密码（BCrypt 哈希），参考 sys_user 方式存储

    @Column("login_error_count")
    private Integer loginErrorCount; // 连续登录失败次数（成功登录清零；用于后续锁定策略）

    @Column("password_updated_at")
    private LocalDateTime passwordUpdatedAt; // 密码最后修改时间，用于密码有效期/强制改密

    @Column("last_login_at")
    private LocalDateTime lastLoginAt; // 最近登录时间

    @Column("create_time")
    private LocalDateTime createTime; // 创建时间

    @Column("update_time")
    private LocalDateTime updateTime; // 更新时间

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }

    public Integer getAdmin() { return admin; }
    public void setAdmin(Integer admin) { this.admin = admin; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Long getLatestRoleId() { return latestRoleId; }
    public void setLatestRoleId(Long latestRoleId) { this.latestRoleId = latestRoleId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Integer getLoginErrorCount() { return loginErrorCount; }
    public void setLoginErrorCount(Integer loginErrorCount) { this.loginErrorCount = loginErrorCount; }

    public LocalDateTime getPasswordUpdatedAt() { return passwordUpdatedAt; }
    public void setPasswordUpdatedAt(LocalDateTime passwordUpdatedAt) { this.passwordUpdatedAt = passwordUpdatedAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
