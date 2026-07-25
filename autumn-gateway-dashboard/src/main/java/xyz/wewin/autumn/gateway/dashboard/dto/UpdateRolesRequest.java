package xyz.wewin.autumn.gateway.dashboard.dto;

import java.util.List;

public class UpdateRolesRequest {
    /** 应用 ID 列表 */
    private List<Long> roleIds;

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
}
