-- ================================================================
-- Autumn Gateway Dashboard — 网关可视化配置表结构（H2 版）
--
-- 目标库：autumn_security（MODE=MySQL，与 basic_* 同库共存）
-- 连接串：jdbc:h2:tcp://localhost:9092/autumn_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
--
-- 这 5 张 gw_* 表只负责「网关 application.properties 的可视化维护」：
--   页面配置 → 落库 → 后续导出 properties/yaml → 同步到文件 / Consul / Nacos
--
-- 设计取舍（v1 精简版，刻意不做的事）：
--   1) 不建过滤器/断言字典表：过滤器工厂是 Spring Bean，新增必须改代码，
--      参数表单 schema（必填/单例/可重复）放在代码侧静态注册表，不落库。
--   2) 不建发布记录表：v1 导出直接写文件/推配置中心，审计快照后面再加。
--   3) 不建独立 profile 表：环境差异在 gw_config_item.profile_code 一列体现。
--
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，可重复执行。
-- ================================================================


-- ----------------------------------------------------------------
-- 表 1：gw_config_item —— 网关全局配置项（key-value，按环境隔离）
--
-- 这张表就是 application.properties 里除 routes 以外的全部内容：
--   server.port、spring.datasource.*、spring.cloud.gateway.httpclient.*、
--   autumn.authn.*、autumn.authz.*、limit.autumn-gateway.* ...
--
-- 设计：每个 (profile_code, config_key) 一行；值统一存字符串，
--       value_type 告诉前端该渲染成输入框/数字框/开关/多行文本/加密框。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_config_item` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `profile_code` VARCHAR(32)  NOT NULL DEFAULT 'dev' COMMENT '环境编码：dev/test/prod；v1 单环境即 dev',
    `config_group` VARCHAR(32)  NOT NULL COMMENT '分组，页面按组折叠：SERVER / DATASOURCE / SCG / AUTHN / AUTHZ / LIMIT / CORS / CUSTOM',
    `config_key`   VARCHAR(128) NOT NULL COMMENT '配置 key，如 autumn.authz.jwt.secret / server.port',
    `config_value` TEXT         NULL     COMMENT '配置值；is_encrypted=1 时存密文，页面掩码显示',
    `value_type`   VARCHAR(16)  NOT NULL DEFAULT 'STRING'
                   COMMENT '值类型，决定前端控件：STRING=输入框 INT/LONG=数字 BOOL=开关 DURATION=时长(5s) LIST=多行标签 JSON=多行 TEXTAREA=大段SQL',
    `is_required`  TINYINT      NOT NULL DEFAULT 0 COMMENT '1=必填，空值拦截保存',
    `is_encrypted` TINYINT     NOT NULL DEFAULT 0 COMMENT '1=敏感值（jwt.secret/密码），落库前加密、页面不回显明文',
    `sort_num`     INT          NOT NULL DEFAULT 0 COMMENT '同组内排序，小者靠前',
    `remark`       VARCHAR(512) NULL     COMMENT '该项的说明，页面 tooltip 用',
    `created_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（由应用层刷新）',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_gw_item_profile_key` UNIQUE (`profile_code`, `config_key`)
);

-- 常用查询：按环境 + 分组拉整页配置
CREATE INDEX IF NOT EXISTS `idx_gw_item_group` ON `gw_config_item` (`profile_code`, `config_group`, `sort_num`);


-- ----------------------------------------------------------------
-- 表 2：gw_route —— 路由（每个下游服务一条）
--
-- 对应 spring.cloud.gateway.routes[i]。一条路由 = 一个下游服务：
--   order-service  → lb://service-order
--   product-service → lb://service-product
--
-- uri 一律写 lb://服务名 走注册中心负载均衡，环境差异由注册中心解决，
-- 所以路由本身不按 profile_code 拆。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_route` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_code`    VARCHAR(64)  NOT NULL COMMENT 'SCG route id，全局唯一，如 order-service',
    `route_name`    VARCHAR(128) NOT NULL COMMENT '显示名，如「订单服务」',
    `app_code`      VARCHAR(64)  NULL     COMMENT '绑定应用编码，关联 basic_app.app_code，可空（纯转发不鉴权的服务不绑）',
    `uri`           VARCHAR(512) NOT NULL COMMENT '目标地址：lb://service-order 走注册中心；http://host:port 直连',
    `order_num`     INT          NOT NULL DEFAULT 0 COMMENT '路由匹配顺序，越小越先匹配；精确路径放小数字、通配放大数字',
    `metadata_json` TEXT         NULL     COMMENT '路由元数据 Map<String,String>，如 {"response-timeout":"3000"} 单路由覆盖超时',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用（停用即从导出的路由列表里摘掉）',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '软删：1=已删；唯一键不含 del_flag，恢复=UPDATE 复活',
    `remark`        VARCHAR(512) NULL,
    `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_gw_route_code` UNIQUE (`route_code`)
);

CREATE INDEX IF NOT EXISTS `idx_gw_route_app` ON `gw_route` (`app_code`, `status`);


-- ----------------------------------------------------------------
-- 表 3：gw_route_predicate —— 路由断言（1 路由 : N 断言）
--
-- 对应 routes[i].predicates[j]。同一路由下多个断言之间是 AND 关系。
-- 参数用 args_json 存，导出时按 predicate_name 翻译：
--   Path        → {"pattern":"/api/order/**"}   → Path=/api/order/**
--   Method      → {"methods":"GET,POST"}         → Method=GET,POST
--   Header      → {"name":"token","regexp":"\\w+"}
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_route_predicate` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_id`       BIGINT       NOT NULL COMMENT '所属路由 gw_route.id',
    `predicate_name` VARCHAR(64)  NOT NULL COMMENT '断言工厂名：Path/Method/Host/Header/Query/Cookie/After/Between/RemoteAddr/Weight',
    `args_json`      TEXT         NULL     COMMENT '断言参数 JSON，结构由代码侧 PredicateDefinition 注册表定义',
    `sort_num`       INT          NOT NULL DEFAULT 0 COMMENT '展示顺序（断言之间 AND，顺序不影响语义，只影响页面排列）',
    `status`         TINYINT      NOT NULL DEFAULT 1,
    `del_flag`       TINYINT      NOT NULL DEFAULT 0,
    `created_at`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_gw_pred_route` FOREIGN KEY (`route_id`) REFERENCES `gw_route` (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_gw_pred_route` ON `gw_route_predicate` (`route_id`, `sort_num`);


-- ----------------------------------------------------------------
-- 表 4：gw_route_filter —— 路由过滤器（1 路由 : N 过滤器，带顺序）
--
-- 对应 routes[i].filters[k]。sort_num 即过滤器链执行顺序，小者先执行。
-- 「某过滤器一条路由只准挂一次」由代码侧注册表 singletonPerRoute=true 控制
-- （Authz / RequestRateLimiter / StripPrefix / CircuitBreaker / Retry），
-- 不在数据库加唯一约束——因为不同工厂名天然不冲突，AddRequestHeader 这类可挂多次。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_route_filter` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_id`    BIGINT       NOT NULL COMMENT '所属路由 gw_route.id',
    `filter_name` VARCHAR(64)  NOT NULL COMMENT '过滤器工厂短名：Authz/StripPrefix/AddRequestHeader/RewritePath/CircuitBreaker/Retry/...',
    `args_json`   TEXT         NULL     COMMENT '参数 JSON，如 {"mode":"authorize","appCode":"order"}',
    `sort_num`    INT          NOT NULL DEFAULT 0 COMMENT '链内顺序，小者先执行',
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `del_flag`    TINYINT      NOT NULL DEFAULT 0,
    `created_at`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_gw_rf_route` FOREIGN KEY (`route_id`) REFERENCES `gw_route` (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_gw_rf_route` ON `gw_route_filter` (`route_id`, `sort_num`);
CREATE INDEX IF NOT EXISTS `idx_gw_rf_name`  ON `gw_route_filter` (`filter_name`);


-- ----------------------------------------------------------------
-- 表 5：gw_global_filter —— 全局默认过滤器
--
-- 对应 spring.cloud.gateway.default-filters：自动挂到每一条路由上，
-- 适合统一加响应头、统一限流默认值、统一异常包装。
-- 和路由过滤器共用同一套工厂名，只是作用域是全局。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_global_filter` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `filter_name` VARCHAR(64)  NOT NULL COMMENT '工厂名，如 AddResponseHeader / RequestRateLimiter',
    `args_json`   TEXT         NULL     COMMENT '参数 JSON',
    `sort_num`    INT          NOT NULL DEFAULT 0 COMMENT 'default-filters 链内顺序，小者先执行',
    `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用（保留记录但不导出）',
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `del_flag`    TINYINT      NOT NULL DEFAULT 0,
    `remark`      VARCHAR(512) NULL,
    `created_at`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_gw_gf_sort` ON `gw_global_filter` (`sort_num`);


-- ================================================================
-- 自检：列出 gw_* 表
-- ================================================================
SELECT 'gw_config_item'     AS table_name, COUNT(*) AS rows_cnt FROM `gw_config_item`
UNION ALL SELECT 'gw_route',            COUNT(*) FROM `gw_route`
UNION ALL SELECT 'gw_route_predicate',  COUNT(*) FROM `gw_route_predicate`
UNION ALL SELECT 'gw_route_filter',     COUNT(*) FROM `gw_route_filter`
UNION ALL SELECT 'gw_global_filter',    COUNT(*) FROM `gw_global_filter`;
