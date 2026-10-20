package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.security.Permissions;
import xyz.wewin.autumn.gateway.dashboard.security.RbacStatusService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限体系自检接口。
 *
 * <p>用途：部署后 / 排查权限问题时，一眼看清「库里有没有初始化」和
 * 「当前登录的人到底有哪些权限」，不用去翻日志和数据库。</p>
 *
 * <pre>
 *   GET /api/system/rbac/status   —— 库里的初始化状态（菜单/资源/角色/授权/账号）
 *   GET /api/system/rbac/me       —— 当前登录者的权限快照
 * </pre>
 */
@RestController
@RequestMapping("/api/system/rbac")
public class RbacStatusController {

    @Autowired
    private RbacStatusService statusService;

    /**
     * 初始化状态。{@code ready=false} 时看 {@code problems} 里的具体原因，
     * 执行 {@code db/rbac/bootstrap-mysql.sh} 即可自举。
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(statusService.check().toMap());
    }

    /** 当前登录者的权限快照：用于验证「我到底有没有这个按钮的权限」 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", Permissions.currentUserId());
        m.put("superAdmin", Permissions.isSuperAdmin());
        List<String> authorities = Permissions.currentAuthorities();
        m.put("authorityCount", authorities.size());
        m.put("authorities", authorities);
        return Result.success(m);
    }
}
