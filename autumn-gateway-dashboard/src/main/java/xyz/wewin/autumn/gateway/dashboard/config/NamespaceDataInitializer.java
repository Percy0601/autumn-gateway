package xyz.wewin.autumn.gateway.dashboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicNamespace;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicNamespaceRepository;
import xyz.wewin.autumn.gateway.dashboard.service.basic.BasicNamespaceService;

import java.time.LocalDateTime;

/**
 * 系统初始化（幂等，可重复执行，每次启动都会跑）：
 * <ol>
 *   <li>保证存在默认租户（命名空间）default；</li>
 *   <li>把存量 basic_app / basic_role / basic_group 中未归属命名空间的记录回填到默认租户
 *       （缺列时只告警不中断启动——说明存量库还没执行 db/migration-v3-to-v4.sql）；</li>
 *   <li>播种登录账号（旧表 user / user_auth_account / user_app / role / user_role），
 *       保证新库或切换数据库后能直接登录；</li>
 *   <li>播种 basic_* 演示数据（命名空间结构就绪时），让系统管理页面非空。</li>
 * </ol>
 *
 * <p>默认管理员：admin / autumn.user.default-password（默认 Autumn@123456）。</p>
 *
 * <p>注意：basic_* 表结构由 spring.sql.init 的 schema 脚本（CREATE TABLE IF NOT EXISTS）
 * 在启动时自动建表；存量库的命名空间列变更需先执行 db/migration-v3-to-v4.sql
 * （需要具备 ALTER 权限的账号）。</p>
 */
@Component
public class NamespaceDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NamespaceDataInitializer.class);

    private static final String DEFAULT_CODE = "default";
    private static final String DEFAULT_NAME = "默认租户";
    private static final String ADMIN_USERNAME = "admin";

    private final BasicNamespaceRepository namespaceRepository;
    private final BasicNamespaceService namespaceService;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;

    public NamespaceDataInitializer(BasicNamespaceRepository namespaceRepository,
                                    BasicNamespaceService namespaceService,
                                    JdbcTemplate jdbcTemplate,
                                    PasswordEncoder passwordEncoder,
                                    @Value("${autumn.user.default-password:Autumn@123456}") String defaultPassword) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceService = namespaceService;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        BasicNamespace def = ensureDefaultNamespace();
        if (def != null && def.getId() != null) {
            backfillNamespaces(def);
        }
        seedLoginData();
        if (def != null && def.getId() != null) {
            seedBasicDemoData(def);
        }
    }

    // ---------- 1. 默认租户 ----------

    private BasicNamespace ensureDefaultNamespace() {
        try {
            return namespaceRepository.findByCode(DEFAULT_CODE)
                    .orElseGet(() -> namespaceService.create(buildDefault()));
        } catch (DataAccessException e) {
            log.warn("默认租户初始化失败（{}），请检查 basic_namespace 表是否可写", e.getMostSpecificCause().getMessage());
            return null;
        }
    }

    private BasicNamespace buildDefault() {
        BasicNamespace ns = new BasicNamespace();
        ns.setCode(DEFAULT_CODE);
        ns.setName(DEFAULT_NAME);
        ns.setDescription("系统初始化默认命名空间");
        ns.setStatus(1);
        return ns;
    }

    // ---------- 2. 存量数据回填到默认租户（缺列不崩溃，只告警） ----------

    private void backfillNamespaces(BasicNamespace def) {
        backfillOne("basic_app", def);
        backfillOne("basic_role", def);
        backfillOne("basic_group", def);
    }

    private void backfillOne(String table, BasicNamespace def) {
        String sql = "UPDATE " + table
                + " SET namespace_id = ?, namespace_code = ? WHERE namespace_id IS NULL OR namespace_id = 0";
        try {
            jdbcTemplate.update(sql, def.getId(), def.getCode());
        } catch (DataAccessException e) {
            log.warn("回填 {} 命名空间失败：{}。大概率是存量库尚未执行 db/migration-v3-to-v4.sql"
                            + "（缺少 namespace_id / namespace_code 列），请用具备 ALTER 权限的账号执行后重启",
                    table, e.getMostSpecificCause().getMessage());
        }
    }

    // ---------- 3. 登录账号种子（旧表，登录链路依赖） ----------

    private void seedLoginData() {
        try {
            Long appId = ensureLegacyApp();
            Long adminId = ensureLegacyUser();
            ensureLegacyAuthAccount(adminId);
            ensureLegacyUserApp(adminId, appId);
            Long roleId = ensureLegacyRole(appId);
            ensureLegacyUserRole(adminId, roleId, appId);
            log.info("登录账号就绪：{}（密码为 autumn.user.default-password 配置值）", ADMIN_USERNAME);
        } catch (DataAccessException e) {
            log.warn("登录种子数据写入失败：{}", e.getMostSpecificCause().getMessage());
        }
    }

    private Long ensureLegacyApp() {
        Long id = queryForLongOrNull("SELECT id FROM application WHERE appid = 'admin'");
        if (id != null) {
            return id;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO application (appid, name, base_path, description, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                ADMIN_USERNAME, "管理后台", "/api", "Autumn Gateway 管理后台", 1, now, now);
        return queryForLongOrNull("SELECT id FROM application WHERE appid = 'admin'");
    }

    private Long ensureLegacyUser() {
        Long id = queryForLongOrNull("SELECT id FROM \"user\" WHERE username = ?", ADMIN_USERNAME);
        if (id != null) {
            return id;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO \"user\" (uuid, username, nickname, avatar, email, phone, emp_no, status,"
                        + " password_updated_at, last_login_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "u-admin-0001", ADMIN_USERNAME, "管理员", "", "admin@autumn.local", "", "E0001", 1,
                null, null, now, now);
        return queryForLongOrNull("SELECT id FROM \"user\" WHERE username = ?", ADMIN_USERNAME);
    }

    private void ensureLegacyAuthAccount(Long adminId) {
        if (adminId == null) {
            return;
        }
        int cnt = count("SELECT COUNT(1) FROM user_auth_account WHERE identity_type = 'password' AND identifier = ?",
                ADMIN_USERNAME);
        if (cnt > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO user_auth_account (user_id, identity_type, identifier, credential, issuer,"
                        + " expires_at, refresh_token, union_id, verified, credential_updated_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                adminId, "password", ADMIN_USERNAME, passwordEncoder.encode(defaultPassword),
                null, null, null, null, 1, now, now, now);
    }

    private void ensureLegacyUserApp(Long adminId, Long appId) {
        if (adminId == null || appId == null) {
            return;
        }
        if (count("SELECT COUNT(1) FROM user_app WHERE user_id = ? AND app_id = ?", adminId, appId) > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO user_app (user_id, app_id, is_admin, created_at) VALUES (?, ?, ?, ?)",
                adminId, appId, 1, LocalDateTime.now());
    }

    private Long ensureLegacyRole(Long appId) {
        if (appId == null) {
            return null;
        }
        Long id = queryForLongOrNull("SELECT id FROM \"role\" WHERE app_id = ? AND code = 'admin'", appId);
        if (id != null) {
            return id;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO \"role\" (app_id, code, name, description, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                appId, "admin", "管理员", "超级管理员", 1, now, now);
        return queryForLongOrNull("SELECT id FROM \"role\" WHERE app_id = ? AND code = 'admin'", appId);
    }

    private void ensureLegacyUserRole(Long adminId, Long roleId, Long appId) {
        if (adminId == null || roleId == null || appId == null) {
            return;
        }
        if (count("SELECT COUNT(1) FROM user_role WHERE user_id = ? AND role_id = ? AND app_id = ?",
                adminId, roleId, appId) > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO user_role (user_id, role_id, app_id, expires_at, created_by, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                adminId, roleId, appId, null, adminId, LocalDateTime.now());
    }

    // ---------- 4. basic_* 演示数据（命名空间结构就绪时才播种） ----------

    private void seedBasicDemoData(BasicNamespace def) {
        if (!basicSchemaReady()) {
            log.warn("basic_* 演示数据未播种：检测到存量库缺少命名空间列，"
                    + "请先执行 db/migration-v3-to-v4.sql（需 ALTER 权限）后重启");
            return;
        }
        try {
            Long nsId = def.getId();
            String nsCode = def.getCode();
            Long basicAppId = ensureBasicApp(nsId, nsCode);
            Long basicUserId = ensureBasicUser();
            Long adminRoleId = ensureBasicRole(nsId, nsCode, "admin", "管理员", 1, "超级管理员", 1);
            ensureBasicRole(nsId, nsCode, "auditor", "审计员", 2, "只读审计", 2);
            ensureBasicMenus(basicAppId);
            ensureBasicResources(basicAppId);
            Long devGroupId = ensureBasicGroup(nsId, nsCode, "dev", "开发组");
            ensureBasicGroup(nsId, nsCode, "ops", "运维组");
            ensureBasicUserRole(basicUserId, adminRoleId);
            ensureBasicUserGroup(basicUserId, devGroupId);
        } catch (DataAccessException e) {
            log.warn("basic_* 演示数据写入失败：{}", e.getMostSpecificCause().getMessage());
        }
    }

    /** basic_app 是否已有命名空间列（即：新库或已执行 v3→v4 迁移） */
    private boolean basicSchemaReady() {
        try {
            return count("SELECT COUNT(1) FROM information_schema.COLUMNS"
                    + " WHERE UPPER(TABLE_NAME) = 'BASIC_APP' AND UPPER(COLUMN_NAME) = 'NAMESPACE_ID'") > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    private Long ensureBasicApp(Long nsId, String nsCode) {
        Long id = queryForLongOrNull("SELECT id FROM basic_app WHERE app_code = 'admin'");
        if (id != null) {
            return id;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO basic_app (app_code, name, base_path, description, status,"
                        + " namespace_id, namespace_code, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "admin", "管理后台", "/api", "平台管理后台", 1, nsId, nsCode, now, now);
        return queryForLongOrNull("SELECT id FROM basic_app WHERE app_code = 'admin'");
    }

    private Long ensureBasicUser() {
        Long id = queryForLongOrNull("SELECT id FROM basic_user WHERE username = ?", ADMIN_USERNAME);
        if (id == null) {
            // 新库：直接写入带 BCrypt 密码的 admin 账号
            LocalDateTime now = LocalDateTime.now();
            jdbcTemplate.update(
                    "INSERT INTO basic_user (uuid, username, nickname, avatar, email, phone, emp_no, admin, status,"
                            + " password, login_error_count, latest_role_id, password_updated_at, last_login_at,"
                            + " create_time, update_time)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "u-admin-0001", ADMIN_USERNAME, "管理员", "", "admin@autumn.local", "", "E0001", 1, 1,
                    passwordEncoder.encode(defaultPassword), 0, 0L, null, null, now, now);
            id = queryForLongOrNull("SELECT id FROM basic_user WHERE username = ?", ADMIN_USERNAME);
        } else {
            // 存量库（v5 之前）：admin 可能还没有 password，补填默认密码
            int hasPwd = count("SELECT COUNT(1) FROM basic_user WHERE id = ? AND password IS NOT NULL AND password <> ''", id);
            if (hasPwd == 0) {
                jdbcTemplate.update("UPDATE basic_user SET password = ? WHERE id = ?",
                        passwordEncoder.encode(defaultPassword), id);
            }
        }
        return id;
    }

    private Long ensureBasicRole(Long nsId, String nsCode, String code, String name,
                                 int type, String remark, int master) {
        Long id = queryForLongOrNull("SELECT id FROM basic_role WHERE namespace_id = ? AND code = ?", nsId, code);
        if (id != null) {
            return id;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO basic_role (namespace_id, namespace_code, code, name, type, status, del_flag,"
                        + " create_user, create_time, update_user, update_time, remark, master)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                nsId, nsCode, code, name, type, 1, 0, 0L, now, 0L, now, remark, master);
        return queryForLongOrNull("SELECT id FROM basic_role WHERE namespace_id = ? AND code = ?", nsId, code);
    }

    /**
     * 播种最小可用菜单集。
     *
     * <p><b>只在 basic_menu 为空时播种</b>：完整的菜单树（含按钮权限点）由
     * {@code db/rbac/V2__rbac_init_data_mysql.sql} 提供，权限标识是
     * {@code system:user:create} 这种「模块:资源:动作」格式。
     * 而这里播种的是 {@code user} / {@code role} 这种短标识的最小集。
     * 两套并存会让前端拿到两组同名的页面路由（用户管理出现两次），
     * 也会让 {@code @PreAuthorize} 比对不到正确的权限码。
     * 因此一旦发现库里已有标准格式的权限点，就完全不插手。</p>
     */
    private void ensureBasicMenus(Long appId) {
        if (appId == null) {
            return;
        }
        if (count("SELECT COUNT(1) FROM basic_menu WHERE del_flag = 0") > 0) {
            log.debug("basic_menu 已有数据（V2 初始化脚本或人工维护），跳过最小菜单集播种");
            return;
        }
        // 系统管理目录（捕获其自增 id，供子菜单挂接）
        Long sysId = ensureMenu(appId, "sys", "系统管理", "", 1, 0L, "setting", "系统管理目录");
        // 首页（顶级菜单，dashboard/index 为看板页）
        ensureMenu(appId, "dashboard", "首页", "dashboard/index", 2, 0L, "home", "");
        // 系统管理下的子菜单（与前端实际页面一致；icon 统一为 iconfont 小写符号名）
        ensureMenu(appId, "namespace", "命名空间", "system/namespace/index", 2, sysId, "apartment", "");
        ensureMenu(appId, "app", "应用管理", "system/app/index", 2, sysId, "appstore", "");
        ensureMenu(appId, "user", "用户管理", "system/user/index", 2, sysId, "user", "");
        ensureMenu(appId, "role", "角色管理", "system/role/index", 2, sysId, "key", "");
        ensureMenu(appId, "menu", "菜单管理", "system/menu/index", 2, sysId, "menu", "");
        ensureMenu(appId, "resource", "资源管理", "system/resource/index", 2, sysId, "file", "");
        ensureMenu(appId, "group", "用户组管理", "system/group/index", 2, sysId, "group", "");
        ensureMenu(appId, "operLog", "日志管理", "system/operLog/index", 2, sysId, "file-text", "");
    }

    /**
     * 幂等写入一条菜单：不存在则插入并返回新 id；已存在则按规范更新名称/跳转/图标等（例如历史数据
     * 图标大小写不统一），并返回其 id。parentId 用捕获到的目录 id 挂接，避免硬编码 1 的脆弱写法。
     */
    private Long ensureMenu(Long appId, String identification, String name, String url,
                            int menuType, long parentId, String icon, String remark) {
        Long id = queryForLongOrNull(
                "SELECT id FROM basic_menu WHERE app_id = ? AND menu_identification = ?", appId, identification);
        LocalDateTime now = LocalDateTime.now();
        if (id == null) {
            jdbcTemplate.update(
                    "INSERT INTO basic_menu (app_id, parent_id, menu_type, menu_name, menu_url, menu_identification,"
                            + " menu_icon, sort_num, menu_status, del_flag, create_user, create_time, update_user,"
                            + " update_time, remark)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    appId, parentId, menuType, name, url, identification, icon, 0, 1, 0, 0L, now, 0L, now, remark);
            return queryForLongOrNull(
                    "SELECT id FROM basic_menu WHERE app_id = ? AND menu_identification = ?", appId, identification);
        }
        jdbcTemplate.update(
                "UPDATE basic_menu SET menu_name = ?, menu_url = ?, menu_icon = ?, menu_type = ?, parent_id = ?,"
                        + " update_time = ? WHERE id = ?",
                name, url, icon, menuType, parentId, now, id);
        return id;
    }

    private void ensureBasicResources(Long appId) {
        if (appId == null) {
            return;
        }
        seedResource(appId, "查询应用", "/api/system/basic/app", "GET", "应用列表查询");
        seedResource(appId, "查询用户", "/api/system/basic/user", "GET", "用户列表查询");
    }

    private void seedResource(Long appId, String name, String path, String httpMethod, String description) {
        if (count("SELECT COUNT(1) FROM basic_resource WHERE app_code = 'admin' AND path = ?", path) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO basic_resource (app_id, app_code, name, path, http_method, match_type,"
                        + " description, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                appId, "admin", name, path, httpMethod, "exact", description, 1, now, now);
    }

    private Long ensureBasicGroup(Long nsId, String nsCode, String code, String name) {
        Long id = queryForLongOrNull("SELECT id FROM basic_group WHERE namespace_id = ? AND code = ?", nsId, code);
        if (id != null) {
            return id;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO basic_group (namespace_id, namespace_code, name, create_time, update_time,"
                        + " deleted, code, org_id, job_id, status)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                nsId, nsCode, name, now, now, 0, code, 0L, 0L, 1);
        return queryForLongOrNull("SELECT id FROM basic_group WHERE namespace_id = ? AND code = ?", nsId, code);
    }

    private void ensureBasicUserRole(Long basicUserId, Long roleId) {
        if (basicUserId == null || roleId == null) {
            return;
        }
        if (count("SELECT COUNT(1) FROM basic_user_role WHERE user_id = ? AND role_id = ? AND app_id = 0",
                basicUserId, roleId) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO basic_user_role (app_id, user_id, role_id, del_flag, create_user, create_time,"
                        + " update_user, update_time, remark)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                0L, basicUserId, roleId, 0, 0L, now, 0L, now, "管理员默认角色");
    }

    private void ensureBasicUserGroup(Long basicUserId, Long groupId) {
        if (basicUserId == null || groupId == null) {
            return;
        }
        if (count("SELECT COUNT(1) FROM basic_user_group WHERE user_id = ? AND group_id = ?",
                basicUserId, groupId) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO basic_user_group (deleted, create_time, update_time, user_id, group_id)"
                        + " VALUES (?, ?, ?, ?, ?)",
                0, now, now, basicUserId, groupId);
    }

    // ---------- 工具 ----------

    private Long queryForLongOrNull(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, args);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private int count(String sql, Object... args) {
        Integer c = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return c == null ? 0 : c;
    }
}
