package xyz.wewin.autumn.gateway.dashboard.entity.gateway;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 结构化全局配置实例（v2），对应表 gw_config_struct。
 * STRUCT 模板（如 globalcors）的实例落点；value_json 为树形 JSON（map 键 + 嵌套 object/list）。
 */
@Table("gw_config_struct")
public class GwConfigStruct {
    @Id
    private Long id;
    @Column("profile_code")
    private String profileCode;
    @Column("struct_name")
    private String structName;
    @Column("instance_name")
    private String instanceName;
    @Column("value_json")
    private String valueJson;
    @Column("sort_num")
    private Integer sortNum;
    private Integer status;
    @Column("del_flag")
    private Integer delFlag;
    private String remark;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProfileCode() { return profileCode; }
    public void setProfileCode(String profileCode) { this.profileCode = profileCode; }
    public String getStructName() { return structName; }
    public void setStructName(String structName) { this.structName = structName; }
    public String getInstanceName() { return instanceName; }
    public void setInstanceName(String instanceName) { this.instanceName = instanceName; }
    public String getValueJson() { return valueJson; }
    public void setValueJson(String valueJson) { this.valueJson = valueJson; }
    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
