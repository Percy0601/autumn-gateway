package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 角色表，对应表 basic_role
 */
@Table("basic_role")
public class BasicRole {
    @Id
    private Long id;
    @Column("namespace_id")
    private Long namespaceId; // 命名空间ID，关联 basic_namespace.id；角色可跨应用，归属命名空间
    @Column("namespace_code")
    private String namespaceCode; // 命名空间编码（冗余存储，便于查询）
    private String code; // 角色编码
    private String name; // 角色名
    private Integer type; // 角色类型：1-系统角色 2-自定义角色
    private Integer status; // 状态：1-启用 2-停用
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
    private Integer master; // 是否是管理员: 1-管理员；2-普通

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getNamespaceId() { return namespaceId; }
    public void setNamespaceId(Long namespaceId) { this.namespaceId = namespaceId; }

    public String getNamespaceCode() { return namespaceCode; }
    public void setNamespaceCode(String namespaceCode) { this.namespaceCode = namespaceCode; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

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

    public Integer getMaster() { return master; }
    public void setMaster(Integer master) { this.master = master; }
}
