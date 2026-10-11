package xyz.wewin.autumn.gateway.dashboard.entity.basic;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 菜单，对应表 basic_menu
 */
@Table("basic_menu")
public class BasicMenu {
    @Id
    private Long id;
    @Column("app_id")
    private Integer appId; // 应用ID
    @Column("parent_id")
    private Long parentId; // 父级ID
    @Column("menu_type")
    private Integer menuType; // 权限类型：1-目录 2-菜单 3-按钮
    @Column("menu_name")
    private String menuName; // 菜单名称
    @Column("menu_url")
    private String menuUrl; // 接口url（多个采用英文逗号分隔）
    @Column("menu_identification")
    private String menuIdentification; // 菜单或按钮标识
    @Column("menu_icon")
    private String menuIcon; // 菜单图标
    @Column("sort_num")
    private Integer sortNum; // 菜单排序
    @Column("menu_status")
    private Integer menuStatus; // 菜单状态：1-启用 2-停用
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

    public Integer getAppId() { return appId; }
    public void setAppId(Integer appId) { this.appId = appId; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public Integer getMenuType() { return menuType; }
    public void setMenuType(Integer menuType) { this.menuType = menuType; }

    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }

    public String getMenuUrl() { return menuUrl; }
    public void setMenuUrl(String menuUrl) { this.menuUrl = menuUrl; }

    public String getMenuIdentification() { return menuIdentification; }
    public void setMenuIdentification(String menuIdentification) { this.menuIdentification = menuIdentification; }

    public String getMenuIcon() { return menuIcon; }
    public void setMenuIcon(String menuIcon) { this.menuIcon = menuIcon; }

    public Integer getSortNum() { return sortNum; }
    public void setSortNum(Integer sortNum) { this.sortNum = sortNum; }

    public Integer getMenuStatus() { return menuStatus; }
    public void setMenuStatus(Integer menuStatus) { this.menuStatus = menuStatus; }

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
