package xyz.wewin.autumn.gateway.dashboard.dto;

import java.util.List;

public class UpdateAppsRequest {
    /** 应用 ID 列表 */
    private List<Long> appIds;

    public List<Long> getAppIds() {
        return appIds;
    }

    public void setAppIds(List<Long> appIds) {
        this.appIds = appIds;
    }
}
