package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 用户组与用户组关联表，对应表 basic_group_group。
 *
 * <p>用于表达用户组之间的上下级关系（组层级）：
 * {@code parent_group_id} 为上级组，{@code group_id} 为下级组。
 * 一个组可以有多个上级、多个下级，构建时需注意避免成环。
 */
@Table("basic_group_group")
public class BasicGroupGroup {
    @Id
    private Long id;
    @Column("parent_group_id")
    private Long parentGroupId; // 上级用户组ID
    @Column("group_id")
    private Long groupId; // 下级用户组ID
    private Integer deleted; // 删除状态：0-否 1-是
    @Column("create_time")
    private LocalDateTime createTime; // 创建时间
    @Column("update_time")
    private LocalDateTime updateTime; // 更新时间
    @Column("op_user_id")
    private Long opUserId; // 操作人

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getParentGroupId() { return parentGroupId; }
    public void setParentGroupId(Long parentGroupId) { this.parentGroupId = parentGroupId; }

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
