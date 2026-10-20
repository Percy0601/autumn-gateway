package xyz.wewin.autumn.gateway.dashboard.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 权限装配服务：把「用户」解析成「可判定的权限集合」。
 *
 * <h3>授权链路</h3>
 * <pre>
 *   basic_user ─┬─ basic_user_role ──────────────┐
 *               │      （直接授角色）             │
 *               └─ basic_user_group               ├─→ 角色集合 ─→ basic_role_permission
 *                      ├─ basic_role_group ───────┘        │
 *                      └─ basic_group_group                  ├─ type=1 → basic_menu  → 权限标识
 *                            （上级组继承角色）               │             └─ basic_menu_resource → 资源
 *                                                           └─ type=2 → basic_resource
 * </pre>
 *
 * <h3>产出</h3>
 * <ul>
 *   <li>{@link UserGrant#authorities()} —— 权限标识集合，形如
 *       {@code system:user:create}，同时供
 *       {@code hasAuthority(...)} 与前端 v-auth 使用；</li>
 *   <li>{@link UserGrant#urlResources()} —— 该用户可访问的 URL 资源，
 *       交给 {@link UrlAuthorizationManager} 做接口级判定；</li>
 *   <li>{@link UserGrant#isSuperAdmin()} —— 超管直通标记。</li>
 * </ul>
 *
 * <h3>缓存</h3>
 * 每次请求都查库代价太高，这里用进程内 {@code ConcurrentHashMap} 做短 TTL 缓存
 * （默认 60 秒，可用 {@code basic.rbac.cache-ttl-seconds} 调整）。
 * 角色/授权变更后需调用 {@link #evict} 让缓存立刻失效——
 * 已在 {@code BasicRoleService} / {@code BasicUserService} 的写方法里挂好调用点。
 *
 * <p><b>为什么不用 Caffeine：</b>本模块已有 caffeine 依赖，但这里只需要「整键失效 + TTL」，
 * 用 JDK 自带结构即可，少一层配置依赖；后续要换 LRU 上限也很容易。</p>
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    /** basic_role_permission.type：1=菜单权限 */
    public static final int PERM_TYPE_MENU = 1;
    /** basic_role_permission.type：2=资源权限 */
    public static final int PERM_TYPE_RESOURCE = 2;

    /**
     * basic_user.admin = 1 或 basic_role.master = 1 时，视为超级管理员。
     *
     * <p>超管不查 basic_role_permission，直接放行全部权限。
     * 这样做有个关键好处：新加菜单/接口时不会因为「忘了给 admin 授权」
     * 把自己锁在系统外面。</p>
     */
    private static final String SQL_IS_SUPER_ADMIN = """
            SELECT COUNT(1) FROM basic_user u
             WHERE u.id = ?
               AND ( u.admin = 1
                     OR EXISTS ( SELECT 1 FROM basic_user_role ur
                                   JOIN basic_role r ON r.id = ur.role_id
                                  WHERE ur.user_id = u.id
                                    AND ur.del_flag = 0
                                    AND r.del_flag = 0
                                    AND r.status = 1
                                    AND r.master = 1 ) )
            """;

    /**
     * 用户的有效角色 id（去重）。
     *
     * <p>注意 ${inherit} 段：group_inherit_enabled = 1 时，
     * 用户所属的组以及该组的**所有下级组**（递归）都参与取角色；
     * 关闭时只看用户直接挂的组。递归用 PostgreSQL / MySQL 8 都支持的
     * WITH RECURSIVE，两库语法一致。</p>
     */
    private static final String SQL_ROLE_IDS = """
            WITH RECURSIVE user_groups AS (
                SELECT ug.group_id
                  FROM basic_user_group ug
                 WHERE ug.user_id = ? AND ug.deleted = 0
                UNION
                SELECT gg.group_id
                  FROM basic_group_group gg
                  JOIN user_groups p ON p.group_id = gg.parent_group_id
                 WHERE gg.deleted = 0
                   AND (? = 1)
            ),
            direct_roles AS (
                SELECT ur.role_id
                  FROM basic_user_role ur
                 WHERE ur.user_id = ? AND ur.del_flag = 0
            ),
            group_roles AS (
                SELECT rg.role_id
                  FROM basic_role_group rg
                 WHERE rg.deleted = 0
                   AND rg.group_id IN (SELECT group_id FROM user_groups)
                   AND (? = 1 OR rg.group_id IN (
                        SELECT ug2.group_id FROM basic_user_group ug2
                         WHERE ug2.user_id = ? AND ug2.deleted = 0))
            )
            SELECT DISTINCT role_id FROM (
                SELECT role_id FROM direct_roles
                UNION
                SELECT role_id FROM group_roles
            ) t
            """;

    /** 全部启用状态的权限标识，供超级管理员装配（见 {@link UserGrant#fullAccess}） */
    private static final String SQL_ALL_MENU_IDENTIFICATIONS = """
            SELECT DISTINCT m.menu_identification
              FROM basic_menu m
             WHERE m.del_flag = 0
               AND m.menu_status = 1
               AND m.menu_identification <> ''
            """;

    /** 角色 → 权限标识（type=1 菜单权限，取 menu_identification） */
    private static final String SQL_MENU_IDENTIFICATIONS = """
            SELECT DISTINCT m.menu_identification
              FROM basic_role_permission p
              JOIN basic_menu m ON m.id = p.ref_id
             WHERE p.role_id IN (%s)
               AND p.type = 1
               AND p.del_flag = 0
               AND m.del_flag = 0
               AND m.menu_status = 1
               AND m.menu_identification <> ''
            """;

    /** 角色 → URL 资源。两条来源：type=2 直接授资源 + type=1 菜单经 basic_menu_resource 带出资源 */
    private static final String SQL_RESOURCES = """
            SELECT DISTINCT r.id, r.name, r.path, r.http_method, r.match_type
              FROM basic_resource r
             WHERE r.status = 1
               AND r.app_code = ?
               AND (
                    r.id IN (
                        SELECT p.ref_id FROM basic_role_permission p
                         WHERE p.role_id IN (%s) AND p.type = 2 AND p.del_flag = 0)
                 OR r.id IN (
                        SELECT mr.resource_id
                          FROM basic_menu_resource mr
                          JOIN basic_role_permission p2 ON p2.ref_id = mr.menu_id
                         WHERE p2.role_id IN (%s) AND p2.type = 1 AND p2.del_flag = 0
                           AND mr.deleted = 0)
               )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final boolean groupInheritEnabled;
    private final String appCode;
    private final long cacheTtlMillis;
    private final ConcurrentHashMap<Long, CachedGrant> cache = new ConcurrentHashMap<>();
    /** 全局共享的「全部权限标识」缓存，仅超管装配时使用 */
    private final AtomicReference<CachedAuthorities> allAuthoritiesCache = new AtomicReference<>();

    public PermissionService(JdbcTemplate jdbcTemplate,
                             @Value("${basic.rbac.group-inherit-enabled:true}") boolean groupInheritEnabled,
                             @Value("${basic.rbac.app-code:dashboard}") String appCode,
                             @Value("${basic.rbac.cache-ttl-seconds:60}") long cacheTtlSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.groupInheritEnabled = groupInheritEnabled;
        this.appCode = appCode;
        this.cacheTtlMillis = Math.max(0, cacheTtlSeconds) * 1000L;
    }

    /**
     * 加载某用户的完整权限。命中缓存直接返回。
     *
     * @param userId basic_user.id
     */
    public UserGrant load(Long userId) {
        if (userId == null || userId <= 0) {
            return UserGrant.empty();
        }
        long now = System.currentTimeMillis();
        CachedGrant cached = cache.get(userId);
        if (cached != null && !cached.expired(now)) {
            return cached.grant();
        }
        UserGrant grant = doLoad(userId);
        if (cacheTtlMillis > 0) {
            cache.put(userId, new CachedGrant(grant, now + cacheTtlMillis));
        }
        return grant;
    }

    /** 让某个用户的权限缓存立刻失效；授权变更后必须调用 */
    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    /** 全量清空，角色/菜单/资源发生结构性变更时用 */
    public void evictAll() {
        cache.clear();
        allAuthoritiesCache.set(null);
    }

    /**
     * 统计启用状态的菜单数，供启动自检用。
     *
     * <p>返回 0 基本等于「RBAC 初始化脚本没跑」，这比让用户登录后看到
     * 满屏 403 再去逐层排查要友好得多。</p>
     */
    public long countActiveMenus() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM basic_menu WHERE del_flag = 0 AND menu_status = 1",
                Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 库里全部启用状态的权限标识，用于给超级管理员装配 authorities。
     *
     * <p>全应用共享一份、按 TTL 缓存：超管是少数派，没必要每次请求都多一条 SQL。
     * 菜单结构变更（新增按钮权限点）后最长 {@code cache-ttl-seconds} 秒生效，
     * 也可以调用 {@link #evictAll()} 立刻失效。</p>
     */
    private Set<String> allMenuIdentifications() {
        long now = System.currentTimeMillis();
        CachedAuthorities cached = allAuthoritiesCache.get();
        if (cached != null && !cached.expired(now)) {
            return cached.value();
        }
        Set<String> all = new LinkedHashSet<>(
                jdbcTemplate.queryForList(SQL_ALL_MENU_IDENTIFICATIONS, String.class));
        allAuthoritiesCache.set(new CachedAuthorities(all, now + cacheTtlMillis));
        return all;
    }

    private UserGrant doLoad(Long userId) {
        Integer superAdmin = jdbcTemplate.queryForObject(SQL_IS_SUPER_ADMIN, Integer.class, userId);
        if (superAdmin != null && superAdmin > 0) {
            // 超管：不查授权表，返回一个「全放行」标记。
            // 但 authorities 里要把库里全部权限标识带上——
            // @PreAuthorize("hasAuthority('x')") 走的是字符串精确比对，
            // 只看得见 authorities，看不懂 superAdmin 标记。
            return UserGrant.fullAccess(allMenuIdentifications());
        }

        int inherit = groupInheritEnabled ? 1 : 0;
        List<Long> roleIds = jdbcTemplate.queryForList(
                SQL_ROLE_IDS, Long.class,
                userId, inherit, userId, inherit, userId);

        if (roleIds == null || roleIds.isEmpty()) {
            log.debug("用户 {} 没有任何有效角色", userId);
            return UserGrant.of(Collections.emptySet(), Collections.emptyList());
        }

        String placeholders = placeholders(roleIds.size());

        Set<String> authorities = new LinkedHashSet<>(
                jdbcTemplate.queryForList(
                        String.format(SQL_MENU_IDENTIFICATIONS, placeholders),
                        String.class,
                        roleIds.toArray()));

        List<UrlResource> resources = jdbcTemplate.query(
                String.format(SQL_RESOURCES, placeholders, placeholders),
                (rs, rowNum) -> new UrlResource(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("path"),
                        rs.getString("http_method"),
                        rs.getString("match_type")),
                concat(appCode, roleIds, roleIds));

        log.debug("用户 {} 载入角色 {}，权限标识 {} 个，URL 资源 {} 个",
                userId, roleIds, authorities.size(), resources.size());
        return UserGrant.of(authorities, resources);
    }

    private static String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    private static Object[] concat(Object head, List<Long> middle, List<Long> tail) {
        Object[] all = new Object[1 + middle.size() + tail.size()];
        all[0] = head;
        int i = 1;
        for (Long v : middle) {
            all[i++] = v;
        }
        for (Long v : tail) {
            all[i++] = v;
        }
        return all;
    }

    private record CachedGrant(UserGrant grant, long expireAt) {
        boolean expired(long now) {
            return now > expireAt;
        }
    }

    private record CachedAuthorities(Set<String> value, long expireAt) {
        boolean expired(long now) {
            return now > expireAt;
        }
    }
}
