package xyz.wewin.autumn.gateway.dashboard.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RBAC 初始化自检。
 *
 * <p>目的：让「权限体系没初始化」这件事在<b>启动日志</b>和<b>一个 HTTP 接口</b>里
 * 直接暴露出来，而不是等用户登录后看到满屏 403 再去逐层排查。</p>
 *
 * <h3>判定标准</h3>
 * <ul>
 *   <li>{@code basic_menu} 有启用记录（且含按钮权限点 menu_type=3）——决定前端菜单与权限码；</li>
 *   <li>{@code basic_resource} 有启用记录——决定 URL 资源鉴权（basic.rbac.url-check=true 时）；</li>
 *   <li>{@code basic_role} 中存在 master=1 的超级管理员角色；</li>
 *   <li>存在可登录账号（basic_user.password 非空）。</li>
 * </ul>
 *
 * <p>本类只读，不做任何写入：自举（bootstrap）由运维执行
 * {@code db/rbac/bootstrap-mysql.sh}，或者由 {@code NamespaceDataInitializer}
 * 在空库上播种最小数据集。</p>
 */
@Service
public class RbacStatusService {

    private static final Logger log = LoggerFactory.getLogger(RbacStatusService.class);

    private final JdbcTemplate jdbcTemplate;

    public RbacStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * RBAC 初始化快照。
     *
     * @param ready    是否达到「可正常使用权限体系」的最低标准
     * @param problems 未达标项的可读描述；为空即全部通过
     */
    public record RbacStatus(long menus,
                             long buttons,
                             long resources,
                             long roles,
                             long superAdminRoles,
                             long rolePermissions,
                             long users,
                             long loginableUsers,
                             boolean ready,
                             List<String> problems) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("menus", menus);
            m.put("buttons", buttons);
            m.put("resources", resources);
            m.put("roles", roles);
            m.put("superAdminRoles", superAdminRoles);
            m.put("rolePermissions", rolePermissions);
            m.put("users", users);
            m.put("loginableUsers", loginableUsers);
            m.put("ready", ready);
            m.put("problems", problems);
            return m;
        }
    }

    /** 采集当前 RBAC 初始化状态；任意一步查询失败都降级为「未就绪」而不是抛异常 */
    public RbacStatus check() {
        long menus = safeCount("SELECT COUNT(1) FROM basic_menu WHERE del_flag = 0 AND menu_status = 1");
        long buttons = safeCount("SELECT COUNT(1) FROM basic_menu WHERE del_flag = 0 AND menu_status = 1 AND menu_type = 3");
        long resources = safeCount("SELECT COUNT(1) FROM basic_resource WHERE status = 1");
        long roles = safeCount("SELECT COUNT(1) FROM basic_role WHERE del_flag = 0");
        long superAdminRoles = safeCount("SELECT COUNT(1) FROM basic_role WHERE del_flag = 0 AND master = 1");
        long rolePermissions = safeCount("SELECT COUNT(1) FROM basic_role_permission WHERE del_flag = 0");
        long users = safeCount("SELECT COUNT(1) FROM basic_user");
        long loginableUsers = safeCount(
                "SELECT COUNT(1) FROM basic_user WHERE password IS NOT NULL AND password <> ''");

        List<String> problems = new ArrayList<>();
        if (menus == 0) {
            problems.add("basic_menu 无启用菜单：前端拿不到动态路由，权限码集合为空");
        }
        if (buttons == 0) {
            problems.add("basic_menu 无按钮权限点（menu_type=3）：@PreAuthorize 将全部拒绝");
        }
        if (roles == 0) {
            problems.add("basic_role 无角色：任何非超管用户都拿不到权限");
        }
        if (superAdminRoles == 0) {
            problems.add("basic_role 中没有 master=1 的超级管理员角色");
        }
        if (loginableUsers == 0) {
            problems.add("basic_user 中没有设置了密码的账号：无人可登录");
        }

        return new RbacStatus(menus, buttons, resources, roles, superAdminRoles,
                rolePermissions, users, loginableUsers, problems.isEmpty(), problems);
    }

    /**
     * 启动完成后自检一次。
     *
     * <p>为什么只在日志里告警而不自动跑初始化脚本：
     * 初始化脚本里有硬编码的 ID 段、初始密码、角色授权矩阵，
     * 应用擅自写入会覆盖运维手工调整过的授权。把「要不要初始化」的决定权
     * 留给人类，程序只负责把事实说清楚。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        RbacStatus status = check();
        if (status.ready()) {
            log.info("RBAC 自检通过：菜单 {} 条（按钮 {} 个）、资源 {} 条、角色 {} 个"
                            + "（超管 {} 个）、授权记录 {} 条、可登录账号 {} 个",
                    status.menus(), status.buttons(), status.resources(), status.roles(),
                    status.superAdminRoles(), status.rolePermissions(), status.loginableUsers());
            return;
        }
        log.warn("=========================================================");
        log.warn("RBAC 未初始化完整，权限体系可能不可用：");
        status.problems().forEach(p -> log.warn("  - {}", p));
        log.warn("=========================================================");
        log.warn("自举方式（任选其一，脚本幂等可重复执行）：");
        log.warn("  1) 一键脚本：sh src/main/resources/db/rbac/bootstrap-mysql.sh --docker mysql");
        log.warn("  2) 手工执行：schema-basic-mysql.sql → V1__rbac_schema_mysql.sql"
                + " → V2__rbac_init_data_mysql.sql → V3__rbac_reset_password_mysql.sql");
        log.warn("  3) 空库场景：应用启动时 NamespaceDataInitializer 会自动播种"
                + "默认租户 + admin 账号 + 最小菜单集，可直接登录");
        log.warn("检查方式：GET /api/system/rbac/status（需登录）");
    }

    private long safeCount(String sql) {
        try {
            Long c = jdbcTemplate.queryForObject(sql, Long.class);
            return c == null ? 0L : c;
        } catch (Exception e) {
            // 表不存在 / 列缺失时返回 0，由 problems 统一呈现，不让启动失败
            log.debug("RBAC 自检查询失败（{}）：{}", sql, e.getMessage());
            return 0L;
        }
    }
}
