package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 用户岗位/职位/Title，对应表 basic_group。
 * 岗位归属命名空间（basic_namespace），按公司/企业隔离。
 */
@Table("basic_group")
public class BasicGroup {
    @Id
    private Long id;
    @Column("namespace_id")
    private Long namespaceId; // 所属命名空间ID，关联 basic_namespace.id
    @Column("namespace_code")
    private String namespaceCode; // 所属命名空间编码（冗余存储，便于查询）
    private String name; // 岗位名称，职位名称，title
    @Column("create_time")
    private LocalDateTime createTime; // 创建时间
    @Column("update_time")
    private LocalDateTime updateTime; // 更新时间
    private Integer deleted; // 是否删除：0-否 1-是
    private String code; // 岗位编码
    @Column("org_id")
    private Long orgId; // 部门名称
    @Column("job_id")
    private Long jobId; // 岗位ID
    private Integer status; // 状态：1-正常 0-停用

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getNamespaceId() { return namespaceId; }
    public void setNamespaceId(Long namespaceId) { this.namespaceId = namespaceId; }

    public String getNamespaceCode() { return namespaceCode; }
    public void setNamespaceCode(String namespaceCode) { this.namespaceCode = namespaceCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
