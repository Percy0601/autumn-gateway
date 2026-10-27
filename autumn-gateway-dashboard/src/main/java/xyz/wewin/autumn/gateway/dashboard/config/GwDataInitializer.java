package xyz.wewin.autumn.gateway.dashboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 网关可视化配置种子数据（幂等，可重复执行，每次启动都跑）。
 *
 * <p>与 {@link NamespaceDataInitializer} 分工：它负责 basic_* / 登录账号；本类只负责 gw_* 表，
 * 以及把「网关配置」菜单挂进 basic_menu（新增菜单行，不改动已有菜单）。</p>
 *
 * <ol>
 *   <li>播种常用过滤器模板 gw_filter_template（scope / singleton_per_route / 参数表单）；</li>
 *   <li>播种常用断言模板 gw_predicate_template；</li>
 *   <li>播种常用全局配置项 gw_config_item（dev 环境示例）；</li>
 *   <li>播种「网关配置」菜单目录 + 4 个子菜单，使前端页面可达。</li>
 * </ol>
 *
 * <p>表结构由 spring.sql.init 在启动时用 db/gateway/gw_schema_*.sql 建好（CREATE TABLE IF NOT EXISTS），
 * 本类在其后运行。若存量库未加载 gw_schema，这里只告警不中断启动。</p>
 */
@Component
public class GwDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GwDataInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public GwDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedFilterTemplates();
            seedPredicateTemplates();
            seedConfigItems();
            seedGatewayMenus();
            log.info("网关配置种子数据就绪（过滤器/断言模板、常用配置项、网关菜单）");
        } catch (DataAccessException e) {
            log.warn("网关种子数据写入失败：{}。大概率是 gw_* 表未创建，"
                            + "请确认 spring.sql.init.schema-locations 已包含 db/gateway/gw_schema_*.sql",
                    e.getMostSpecificCause().getMessage());
        }
    }

    // ==================== 1. 过滤器模板 ====================

    private void seedFilterTemplates() {
        seedFilter("Authz", "网关鉴权", "ROUTE", 1,
                """
                [{"key":"mode","label":"模式","type":"enum","required":true,"options":["authenticate","authorize"],"default":"authenticate"},
                 {"key":"appCode","label":"应用编码","type":"string","required":true}]
                """,
                "认证/鉴权过滤器，每路由一次");
        seedFilter("RequestRateLimiter", "请求限流", "BOTH", 1,
                """
                [{"key":"replenishRate","label":"令牌补充/秒","type":"int","required":true,"default":1},
                 {"key":"burstCapacity","label":"桶容量","type":"int","required":true,"default":2},
                 {"key":"timeWindowSeconds","label":"时间窗口(秒)","type":"long","required":false,"default":10}]
                """,
                "基于令牌桶的限流，路由级或全局默认均可");
        seedFilter("AddRequestHeader", "添加请求头", "ROUTE", 0,
                """
                [{"key":"name","label":"Header 名","type":"string","required":true},
                 {"key":"value","label":"Header 值","type":"string","required":true}]
                """,
                "可挂多次，向下游透传多个头");
        seedFilter("AddResponseHeader", "添加响应头", "BOTH", 0,
                """
                [{"key":"name","label":"Header 名","type":"string","required":true},
                 {"key":"value","label":"Header 值","type":"string","required":true}]
                """,
                "可挂多次，统一响应头常用");
        seedFilter("StripPrefix", "剥离前缀", "ROUTE", 1,
                """
                [{"key":"parts","label":"剥离段数","type":"int","required":true,"default":1}]
                """,
                "去掉 /api 这类网关前缀");
        seedFilter("PrefixPath", "添加前缀", "ROUTE", 1,
                """
                [{"key":"prefix","label":"前缀","type":"string","required":true}]
                """,
                "在转发前给路径加前缀");
        seedFilter("RewritePath", "重写路径", "ROUTE", 1,
                """
                [{"key":"regexp","label":"正则","type":"string","required":true},
                 {"key":"replacement","label":"替换","type":"string","required":true}]
                """,
                "按正则重写路径");
        seedFilter("SetPath", "设置路径", "ROUTE", 1,
                """
                [{"key":"template","label":"路径模板","type":"string","required":true}]
                """,
                "用 URI 模板设置路径");
        seedFilter("RedirectTo", "重定向", "ROUTE", 1,
                """
                [{"key":"status","label":"状态码","type":"int","required":true,"default":302},
                 {"key":"url","label":"跳转地址","type":"string","required":true}]
                """,
                "3xx 重定向");
        seedFilter("CircuitBreaker", "熔断降级", "ROUTE", 1,
                """
                [{"key":"name","label":"熔断名","type":"string","required":true},
                 {"key":"fallbackUri","label":"降级地址","type":"string","required":false}]
                """,
                "Resilience4j 熔断");
        seedFilter("Retry", "重试", "ROUTE", 1,
                """
                [{"key":"retries","label":"次数","type":"int","required":false,"default":3},
                 {"key":"statuses","label":"重试状态码","type":"list","itemType":"int","required":false,"placeholder":"500"}]
                """,
                "按状态码重试，可配多个（如 500,502,503）");
        seedFilter("RequestSize", "请求体大小限制", "ROUTE", 1,
                """
                [{"key":"maxSize","label":"最大字节数","type":"string","required":true}]
                """,
                "限制请求体大小");
    }

    private void seedFilter(String filterName, String displayName, String scope, int singleton,
                            String argsSchemaJson, String remark) {
        if (count("SELECT COUNT(1) FROM gw_filter_template WHERE filter_name = ?", filterName) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO gw_filter_template (filter_name, display_name, scope, singleton_per_route,"
                        + " args_schema_json, status, remark, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                filterName, displayName, scope, singleton, argsSchemaJson, 1, remark, now, now);
    }

    // ==================== 2. 断言模板 ====================

    private void seedPredicateTemplates() {
        seedPredicate("Path", "路径匹配",
                """
                [{"key":"patterns","label":"路径模式","type":"list","itemType":"string","required":true,"placeholder":"/api/order/**"}]
                """,
                "Ant 风格路径匹配，可配多个模式（多条断言间 AND，通常一条足够）");
        seedPredicate("Method", "请求方法",
                """
                [{"key":"methods","label":"HTTP 方法","type":"list","itemType":"enum","itemOptions":["GET","POST","PUT","DELETE","PATCH","OPTIONS","HEAD"],"required":true}]
                """,
                "匹配的 HTTP 方法，可配多个（OR 关系）");
        seedPredicate("Host", "Host 匹配",
                """
                [{"key":"patterns","label":"Host 模式","type":"list","itemType":"string","required":true,"placeholder":"**.somehost.org"}]
                """,
                "按 Host 头匹配，可配多个模式");
        seedPredicate("Header", "请求头匹配",
                """
                [{"key":"name","label":"Header 名","type":"string","required":true},
                 {"key":"regexp","label":"正则","type":"string","required":false}]
                """,
                "按请求头名（可选正则）匹配");
        seedPredicate("Query", "参数匹配",
                """
                [{"key":"param","label":"参数名","type":"string","required":true},
                 {"key":"regexp","label":"正则","type":"string","required":false}]
                """,
                "按查询参数匹配");
        seedPredicate("Cookie", "Cookie 匹配",
                """
                [{"key":"name","label":"Cookie 名","type":"string","required":true},
                 {"key":"regexp","label":"正则","type":"string","required":false}]
                """,
                "按 Cookie 匹配");
        seedPredicate("RemoteAddr", "来源 IP",
                """
                [{"key":"sources","label":"CIDR 段","type":"list","itemType":"string","required":true,"placeholder":"192.168.1.1/24"}]
                """,
                "按来源 IP 段匹配，可配多个 CIDR");
        seedPredicate("After", "在时间之后",
                """
                [{"key":"datetime","label":"时间","type":"string","required":true,"placeholder":"2026-01-01T00:00:00+08:00"}]
                """,
                "仅在该时间之后生效");
        seedPredicate("Before", "在时间之前",
                """
                [{"key":"datetime","label":"时间","type":"string","required":true,"placeholder":"2026-12-31T23:59:59+08:00"}]
                """,
                "仅在该时间之前生效");
        seedPredicate("Between", "时间区间",
                """
                [{"key":"datetime1","label":"起始时间","type":"string","required":true},
                 {"key":"datetime2","label":"结束时间","type":"string","required":true}]
                """,
                "仅在起止时间之间生效");
        seedPredicate("Weight", "权重路由",
                """
                [{"key":"group","label":"权重分组","type":"string","required":true},
                 {"key":"weight","label":"权重","type":"int","required":true}]
                """,
                "灰度/金丝雀按权重分流");
    }

    private void seedPredicate(String predicateName, String displayName, String argsSchemaJson, String remark) {
        if (count("SELECT COUNT(1) FROM gw_predicate_template WHERE predicate_name = ?", predicateName) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO gw_predicate_template (predicate_name, display_name, args_schema_json, status,"
                        + " remark, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                predicateName, displayName, argsSchemaJson, 1, remark, now, now);
    }

    // ==================== 3. 全局配置项（dev 示例） ====================

    private void seedConfigItems() {
        // SERVER
        seedConfig("SERVER", "server.port", "8080", "INT", 1, 0, 1, "网关监听端口");
        seedConfig("SERVER", "spring.application.name", "autumn-gateway", "STRING", 1, 0, 2, "应用名，注册到 Consul/Nacos 用");
        // SCG（Spring Cloud Gateway 5 / Boot 4 的 httpclient 前缀）
        seedConfig("SCG", "spring.cloud.gateway.server.webflux.httpclient.connect-timeout", "2000ms", "DURATION", 0, 0, 1, "连接超时");
        seedConfig("SCG", "spring.cloud.gateway.server.webflux.httpclient.response-timeout", "30s", "DURATION", 0, 0, 2, "响应超时");
        seedConfig("SCG", "spring.cloud.gateway.server.webflux.httpclient.pool.max-connections", "1000", "INT", 0, 0, 3, "连接池最大连接数");
        seedConfig("SCG", "spring.cloud.gateway.server.webflux.httpclient.pool.max-idle-time", "30s", "DURATION", 0, 0, 4, "连接池空闲时间");
        seedConfig("SCG", "spring.cloud.gateway.server.webflux.httpclient.pool.acquire-timeout", "45s", "DURATION", 0, 0, 5, "获取连接超时");
        // AUTHN（OAuth2 登录，autumn.authn.*）
        seedConfig("AUTHN", "autumn.authn.enabled", "false", "BOOL", 0, 0, 1, "是否启用 OAuth2 登录");
        seedConfig("AUTHN", "autumn.authn.issuer-uri", "http://localhost:9000", "STRING", 0, 0, 2, "OIDC 签发方");
        seedConfig("AUTHN", "autumn.authn.client-id", "web-client", "STRING", 0, 0, 3, "OAuth2 client id");
        // AUTHZ（自研鉴权，autumn.authz.*）
        seedConfig("AUTHZ", "autumn.authz.enabled", "true", "BOOL", 1, 0, 1, "是否启用自研鉴权过滤器");
        seedConfig("AUTHZ", "autumn.authz.jwt.secret", "", "STRING", 1, 1, 2, "JWT 共享密钥（敏感，掩码显示）");
        seedConfig("AUTHZ", "autumn.authz.jwt.issuer", "autumn-gateway", "STRING", 0, 0, 3, "JWT 签发方");
        seedConfig("AUTHZ", "autumn.authz.jwt.ttl-seconds", "86400", "LONG", 0, 0, 4, "JWT 有效期（秒）");
        seedConfig("AUTHZ", "autumn.authz.max-login-attempts", "5", "INT", 0, 0, 5, "连续登录失败上限");
        seedConfig("AUTHZ", "autumn.authz.login-app-code", "dashboard", "STRING", 0, 0, 6, "登录所属应用编码");
        // LIMIT（限流 starter）
        seedConfig("LIMIT", "limit.autumn-gateway.enabled", "true", "BOOL", 0, 0, 1, "是否启用默认限流");
        seedConfig("LIMIT", "limit.autumn-gateway.replenish-rate", "1", "INT", 0, 0, 2, "默认令牌补充速率");
        seedConfig("LIMIT", "limit.autumn-gateway.burst-capacity", "2", "INT", 0, 0, 3, "默认桶容量");
        seedConfig("LIMIT", "limit.autumn-gateway.time-window-seconds", "10", "LONG", 0, 0, 4, "默认时间窗口（秒）");
        // DISCOVERY（Consul 连接）
        seedConfig("DISCOVERY", "consul.host", "localhost", "STRING", 1, 0, 1, "Consul 地址");
        seedConfig("DISCOVERY", "consul.port", "8500", "INT", 1, 0, 2, "Consul 端口");
        seedConfig("DISCOVERY", "consul.scheme", "http", "STRING", 0, 0, 3, "Consul 协议");
        seedConfig("DISCOVERY", "consul.token", "", "STRING", 0, 1, 4, "Consul ACL Token（敏感）");
        seedConfig("DISCOVERY", "consul.timeout", "3s", "DURATION", 0, 0, 5, "Consul 请求超时");
    }

    private void seedConfig(String group, String key, String value, String valueType,
                            int required, int encrypted, int sortNum, String remark) {
        if (count("SELECT COUNT(1) FROM gw_config_item WHERE profile_code = 'dev' AND config_key = ?", key) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO gw_config_item (profile_code, config_group, config_key, config_value, value_type,"
                        + " is_required, is_encrypted, sort_num, remark, created_at, updated_at)"
                        + " VALUES ('dev', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                group, key, value, valueType, required, encrypted, sortNum, remark, now, now);
    }

    // ==================== 4. 网关菜单 ====================

    private void seedGatewayMenus() {
        long appId = resolveAdminAppId();
        long dirId = ensureMenu(appId, "gateway", "网关配置", "", 1, 0L, "connection", "网关可视化配置目录");
        ensureMenu(appId, "gateway:route", "网关路由", "gateway/route/index", 2, dirId, "guide", "路由/断言/过滤器管理");
        ensureMenu(appId, "gateway:config", "全局配置", "gateway/config/index", 2, dirId, "setting", "网关全局配置项");
        ensureMenu(appId, "gateway:globalFilter", "全局过滤器", "gateway/globalFilter/index", 2, dirId, "filter", "default-filters");
        ensureMenu(appId, "gateway:center", "配置中心", "gateway/center/index", 2, dirId, "link", "Consul/Nacos 连接管理");
    }

    /** 网关菜单挂到 admin 应用下；找不到 admin 时退回任意启用应用，再退回 app_id=0（findAll 仍可见） */
    private long resolveAdminAppId() {
        Long id = queryForLongOrNull("SELECT id FROM basic_app WHERE app_code = 'admin' AND status = 1");
        if (id == null) {
            id = queryForLongOrNull("SELECT id FROM basic_app WHERE status = 1 ORDER BY id LIMIT 1");
        }
        return id == null ? 0L : id;
    }

    /** 幂等写入菜单：不存在则插入并返回 id；已存在则返回其 id */
    private long ensureMenu(long appId, String identification, String name, String url,
                            int menuType, long parentId, String icon, String remark) {
        Long id = queryForLongOrNull(
                "SELECT id FROM basic_menu WHERE menu_identification = ? AND parent_id = ?",
                identification, parentId);
        if (id != null) {
            return id;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO basic_menu (app_id, parent_id, menu_type, menu_name, menu_url, menu_identification,"
                        + " menu_icon, sort_num, menu_status, del_flag, create_user, create_time, update_user,"
                        + " update_time, remark)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                appId, parentId, menuType, name, url, identification, icon, 0, 1, 0, 0L, now, 0L, now, remark);
        return queryForLongOrNull(
                "SELECT id FROM basic_menu WHERE menu_identification = ? AND parent_id = ?",
                identification, parentId);
    }

    // ==================== 工具 ====================

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
