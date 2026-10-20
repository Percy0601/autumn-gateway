package xyz.wewin.autumn.gateway.dashboard.security;

/**
 * 权限标识（permission code）常量表。
 *
 * <p>这里每一个常量的值都<b>必须</b>与 {@code basic_menu.menu_identification} 里的数据一致，
 * 权威来源是初始化脚本 {@code db/rbac/V2__rbac_init_data_mysql.sql}（按钮权限点，menu_type=3）。
 * 用常量而不是到处写字符串，是为了让「改权限码」这件事只需要改一个地方，
 * 也方便 IDE 全局检索「哪些接口用了这个权限」。</p>
 *
 * <h3>命名规范</h3>
 * <pre>
 *   &lt;模块&gt;:&lt;资源&gt;:&lt;动作&gt;
 *   动作取值：view / create / update / delete / enable / disable / assign-* / reset-pwd / export / toggle
 * </pre>
 *
 * <h3>三层管控策略（见 docs/权限与授权设计.md）</h3>
 * <ol>
 *   <li><b>认证即可</b>：查询（view）与新增/编辑（create/update）——登录就能用，不校验权限码；</li>
 *   <li><b>权限管控</b>：授权类（assign-*）、删除（delete）、启停（enable/disable）、
 *       重置密码（reset-pwd）——必须显式授权；</li>
 *   <li><b>超管直通</b>：{@code basic_user.admin=1} 或角色 {@code basic_role.master=1}，
 *       由 {@link UserGrant#has} 短路放行。</li>
 * </ol>
 */
public final class PermCodes {

    private PermCodes() {
    }

    // ---------- 用户 ----------
    public static final String USER_VIEW = "system:user:view";
    public static final String USER_CREATE = "system:user:create";
    public static final String USER_UPDATE = "system:user:update";
    /** 删除用户：高危 */
    public static final String USER_DELETE = "system:user:delete";
    public static final String USER_ENABLE = "system:user:enable";
    public static final String USER_DISABLE = "system:user:disable";
    /** 给用户分配角色：授权类，必须管控 */
    public static final String USER_ASSIGN_ROLE = "system:user:assign-role";
    /** 给用户分配用户组：授权类，必须管控 */
    public static final String USER_ASSIGN_GROUP = "system:user:assign-group";
    /** 重置密码：高危，必须管控 */
    public static final String USER_RESET_PWD = "system:user:reset-pwd";

    // ---------- 角色 ----------
    public static final String ROLE_VIEW = "system:role:view";
    public static final String ROLE_CREATE = "system:role:create";
    public static final String ROLE_UPDATE = "system:role:update";
    public static final String ROLE_ENABLE = "system:role:enable";
    public static final String ROLE_DISABLE = "system:role:disable";
    /** 给角色分配用户：授权类 */
    public static final String ROLE_ASSIGN_USER = "system:role:assign-user";
    /** 给角色分配菜单：授权类 */
    public static final String ROLE_ASSIGN_MENU = "system:role:assign-menu";
    /** 给角色分配资源：授权类 */
    public static final String ROLE_ASSIGN_RESOURCE = "system:role:assign-resource";
    /** 给角色分配用户组：授权类 */
    public static final String ROLE_ASSIGN_GROUP = "system:role:assign-group";

    // ---------- 用户组 ----------
    public static final String GROUP_VIEW = "system:group:view";
    public static final String GROUP_CREATE = "system:group:create";
    public static final String GROUP_UPDATE = "system:group:update";
    public static final String GROUP_DELETE = "system:group:delete";
    public static final String GROUP_ENABLE = "system:group:enable";
    public static final String GROUP_DISABLE = "system:group:disable";
    /** 给用户组分配用户：授权类 */
    public static final String GROUP_ASSIGN_USER = "system:group:assign-user";
    /** 给用户组分配角色：授权类 */
    public static final String GROUP_ASSIGN_ROLE = "system:group:assign-role";
    /** 维护用户组上下级层级：授权类 */
    public static final String GROUP_ASSIGN_SUB = "system:group:assign-sub";

    // ---------- 菜单 ----------
    public static final String MENU_VIEW = "system:menu:view";
    public static final String MENU_CREATE = "system:menu:create";
    public static final String MENU_UPDATE = "system:menu:update";
    public static final String MENU_ENABLE = "system:menu:enable";
    public static final String MENU_DISABLE = "system:menu:disable";
    /** 菜单关联后台资源：授权类 */
    public static final String MENU_ASSIGN_RESOURCE = "system:menu:assign-resource";

    // ---------- 资源 ----------
    public static final String RESOURCE_VIEW = "system:resource:view";
    public static final String RESOURCE_CREATE = "system:resource:create";
    public static final String RESOURCE_UPDATE = "system:resource:update";
    public static final String RESOURCE_ENABLE = "system:resource:enable";
    public static final String RESOURCE_DISABLE = "system:resource:disable";
    /** 资源关联菜单：授权类 */
    public static final String RESOURCE_ASSIGN_MENU = "system:resource:assign-menu";

    // ---------- 应用 / 命名空间 / 日志 ----------
    public static final String APP_VIEW = "system:app:view";
    public static final String APP_CREATE = "system:app:create";
    public static final String APP_UPDATE = "system:app:update";
    public static final String APP_TOGGLE = "system:app:toggle";
    public static final String NAMESPACE_VIEW = "system:namespace:view";
    public static final String NAMESPACE_CREATE = "system:namespace:create";
    public static final String NAMESPACE_UPDATE = "system:namespace:update";
    public static final String NAMESPACE_TOGGLE = "system:namespace:toggle";
    public static final String LOG_VIEW = "system:log:view";
    public static final String LOG_EXPORT = "system:log:export";

    // ---------- 其它 ----------
    public static final String HOME_VIEW = "dashboard:home:view";
    public static final String SYSTEM_ROOT_VIEW = "system:root:view";
    public static final String OPS_ROOT_VIEW = "ops:root:view";
}
