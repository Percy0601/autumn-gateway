package xyz.wewin.autumn.gateway.authz.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关鉴权配置（前缀 {@code autumn.authz}）。
 *
 * <h3>设计取向</h3>
 * <ul>
 *   <li><b>不注册 GlobalFilter</b>：鉴权只挂在显式声明了 {@code Authz=...} 的路由上，
 *       没挂的路由就是纯匿名转发——想保护谁就给谁加一行，不会误伤健康检查、静态资源；</li>
 *   <li><b>不注册 SecurityWebFilterChain</b>：本模块不接管 Spring Security 的过滤器链，
 *       因此可以和 authn starter（OAuth2 客户端登录）共存而不互相覆盖；</li>
 *   <li><b>SQL 全量外置</b>：所有 JDBC 语句以及固定列别名契约都在 {@link Sql} 里，
 *       表结构不同（或想加租户隔离）时改配置即可，不必改代码。</li>
 * </ul>
 *
 * <h3>列别名即契约</h3>
 * 覆盖 SQL 时必须保留别名，读取端按别名取值：
 * <pre>
 *   用户查询   user_id, subject, username, password_hash, status, login_error_count, admin
 *   角色查询   role_id, role_code, role_master
 *   资源查询   resource_path, http_method, match_type
 * </pre>
 */
@ConfigurationProperties(prefix = "autumn.authz")
public class AuthzGatewayProperties {

    /** 总开关：false 时不注册任何鉴权 Bean（网关退化为纯路由） */
    private boolean enabled = true;

    /** 路由未显式给出 appCode 时的兜底应用编码 */
    private String appCode = "dashboard";

    /** 登录时装配 authorities 所用的应用编码（登录不绑定具体路由，只能取一个默认应用） */
    private String loginAppCode = "dashboard";

    /** 登录失败次数上限：密码错误一次 -1，减到 0 把 status 置 0；登录成功恢复 */
    private int maxLoginAttempts = 5;

    private final Jwt jwt = new Jwt();
    private final Headers headers = new Headers();
    private final Captcha captcha = new Captcha();
    private final Sql sql = new Sql();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppCode() {
        return this.appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getLoginAppCode() {
        return this.loginAppCode;
    }

    public void setLoginAppCode(String loginAppCode) {
        this.loginAppCode = loginAppCode;
    }

    public int getMaxLoginAttempts() {
        return this.maxLoginAttempts;
    }

    public void setMaxLoginAttempts(int maxLoginAttempts) {
        this.maxLoginAttempts = maxLoginAttempts;
    }

    public Jwt getJwt() {
        return this.jwt;
    }

    public Headers getHeaders() {
        return this.headers;
    }

    public Captcha getCaptcha() {
        return this.captcha;
    }

    public Sql getSql() {
        return this.sql;
    }

    /** JWT：共享密钥 HS256，签名与验签用同一把 key */
    public static class Jwt {

        /**
         * HMAC 共享密钥。HS256 要求不少于 32 字节（256 bit），不足会在启动时直接报错——
         * 弱密钥的 JWT 等于没有签名。默认不提供密钥，必须通过配置中心或环境变量显式下发。
         */
        private String secret = "";

        private String issuer = "autumn-gateway";

        /** 令牌有效期（秒），默认 24 小时 */
        private long ttlSeconds = 86_400L;

        public String getSecret() {
            return this.secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return this.issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public long getTtlSeconds() {
            return this.ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    /**
     * 可信身份头：过滤器校验通过后<b>覆盖写入</b>（先删除同名入站头，杜绝伪造），
     * 下游服务可以直接信任这三个头，不必再验一次 JWT。
     */
    public static class Headers {

        private String userId = "X-Autumn-User-Id";

        private String username = "X-Autumn-Username";

        /** 角色编码，逗号分隔，如 {@code ROLE_ADMIN,ops} */
        private String roles = "X-Autumn-Roles";

        public String getUserId() {
            return this.userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return this.username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRoles() {
            return this.roles;
        }

        public void setRoles(String roles) {
            this.roles = roles;
        }
    }

    /** 登录验证码（SVG，与 dashboard 的 CaptchaService 同一套画法） */
    public static class Captcha {

        private boolean enabled = true;

        /** 验证码字符数 */
        private int length = 4;

        /** 有效期（秒）；Cookie Max-Age 与内存 TTL 保持一致 */
        private long ttlSeconds = 180L;

        /** 下发 captchaId 的 Cookie 名（HttpOnly，前端无需感知） */
        private String cookieName = "captchaId";

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLength() {
            return this.length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public long getTtlSeconds() {
            return this.ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public String getCookieName() {
            return this.cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }
    }

    /**
     * 全部 JDBC 语句。默认版本适配 dashboard 的 basic_* 表：
     * {@code basic_user / basic_user_role / basic_role / basic_role_permission /
     * basic_resource / basic_menu_resource}。
     *
     * <p>参数顺序同样是契约，覆盖时不可调换：</p>
     * <pre>
     *   user-query          1 个参数：username
     *   roles-query         2 个参数：userId, appCode
     *   resource-query      3 个参数：appCode, roleId, roleId（type=2 直授资源 / type=1 菜单带出资源）
     *   login-failure-sql   3 个参数：nextRemaining, nextStatus, userId
     *   login-success-sql   2 个参数：maxAttempts, userId
     * </pre>
     */
    public static class Sql {

        private String userQuery = """
                SELECT u.id AS user_id,
                       u.uuid AS subject,
                       u.username AS username,
                       u.password AS password_hash,
                       u.status AS status,
                       u.login_error_count AS login_error_count,
                       u.admin AS admin
                  FROM basic_user u
                 WHERE u.username = ?
                """;

        private String rolesQuery = """
                SELECT DISTINCT r.id AS role_id,
                                r.code AS role_code,
                                r.master AS role_master
                  FROM basic_user_role ur
                  JOIN basic_role r ON r.id = ur.role_id
                 WHERE ur.user_id = ?
                   AND ur.del_flag = 0
                   AND r.del_flag = 0
                   AND r.status = 1
                   AND ur.app_id IN (SELECT a.id FROM basic_app a WHERE a.app_code = ?)
                """;

        private String resourceQuery = """
                SELECT DISTINCT r.path AS resource_path,
                                r.http_method AS http_method,
                                r.match_type AS match_type
                  FROM basic_resource r
                 WHERE r.status = 1
                   AND r.app_code = ?
                   AND ( r.id IN (SELECT p.ref_id
                                    FROM basic_role_permission p
                                   WHERE p.role_id = ? AND p.type = 2 AND p.del_flag = 0)
                      OR r.id IN (SELECT mr.resource_id
                                    FROM basic_menu_resource mr
                                    JOIN basic_role_permission p2 ON p2.ref_id = mr.menu_id
                                   WHERE p2.role_id = ? AND p2.type = 1 AND p2.del_flag = 0
                                     AND mr.deleted = 0) )
                """;

        /** 登录失败：扣减剩余次数；次数归零时把 status 置 0（账号锁定） */
        private String loginFailureSql = """
                UPDATE basic_user
                   SET login_error_count = ?,
                       status = ?,
                       update_time = CURRENT_TIMESTAMP
                 WHERE id = ?
                """;

        /** 登录成功：恢复重试次数并刷新 last_login_at */
        private String loginSuccessSql = """
                UPDATE basic_user
                   SET login_error_count = ?,
                       last_login_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """;

        public String getUserQuery() {
            return this.userQuery;
        }

        public void setUserQuery(String userQuery) {
            this.userQuery = userQuery;
        }

        public String getRolesQuery() {
            return this.rolesQuery;
        }

        public void setRolesQuery(String rolesQuery) {
            this.rolesQuery = rolesQuery;
        }

        public String getResourceQuery() {
            return this.resourceQuery;
        }

        public void setResourceQuery(String resourceQuery) {
            this.resourceQuery = resourceQuery;
        }

        public String getLoginFailureSql() {
            return this.loginFailureSql;
        }

        public void setLoginFailureSql(String loginFailureSql) {
            this.loginFailureSql = loginFailureSql;
        }

        public String getLoginSuccessSql() {
            return this.loginSuccessSql;
        }

        public void setLoginSuccessSql(String loginSuccessSql) {
            this.loginSuccessSql = loginSuccessSql;
        }
    }
}
