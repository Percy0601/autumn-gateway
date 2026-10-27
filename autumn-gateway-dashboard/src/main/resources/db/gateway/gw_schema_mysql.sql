-- ================================================================
-- Autumn Gateway Dashboard — 网关可视化配置表结构（MySQL 8.0+）
--
-- 这 8 张 gw_* 表只负责「网关 application.properties 的可视化维护」：
--   页面配置 → 落库 → 后续导出 properties/yaml → 同步到文件 / Consul / Nacos
--
-- 表清单（依赖顺序）：
--   1) gw_config_center       配置中心 / 注册中心连接（Consul / Nacos）
--   2) gw_config_item         网关全局配置项（key-value，按环境隔离）
--   3) gw_route               路由（每个下游服务一条）
--   4) gw_route_predicate     路由断言（1 路由 : N，断言间 AND）
--   5) gw_route_filter        路由级过滤器（1 路由 : N，sort_num 即链顺序）
--   6) gw_global_filter       全局默认过滤器 default-filters（自动挂到每条路由）
--   7) gw_filter_template     过滤器模板（参数表单定义，前端据此动态渲染）
--   8) gw_predicate_template  断言模板（参数表单定义）
--
-- 关于「必填 / 一次 / 多次」口径（需求第 4 条）：
--   * 必填   —— 配置项用 gw_config_item.is_required；过滤器/断言参数用
--              gw_filter_template.args_schema_json 里每个字段的 required=true；
--   * 一次   —— 过滤器用 gw_filter_template.singleton_per_route=1 表达
--              「同一路由最多挂一次」（如 Authz / StripPrefix / RewritePath）；
--   * 多次   —— singleton_per_route=0 表达「同一路由可挂多次」
--              （如 AddRequestHeader，可加多个 header）。
--   断言天然可重复（AND 关系），不再区分一次/多次。
--
-- 关于「服务级过滤器 / 全局过滤器」口径（需求第 6 条）：
--   * 路由级过滤器：gw_route_filter（挂在某条路由/服务上，只对该路由生效）；
--   * 全局默认过滤器：gw_global_filter（default-filters，自动作用于所有路由）；
--   * 过滤器模板 gw_filter_template.scope 标记其适用范围：
--       ROUTE=仅路由  GLOBAL=仅全局  BOTH=两者皆可。
--
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，可随 spring.sql.init.mode=always 重复执行。
-- 索引名带 gw_ 前缀（H2 的索引名是 SCHEMA 级全局唯一，保持一致避免重名冲突）。
-- 种子数据（常用过滤器/断言模板、常用配置项）由 Java 侧 GwDataInitializer 幂等播种，
--   不放在本脚本里，避免每次启动重复 INSERT。
-- ================================================================


-- ----------------------------------------------------------------
-- 表 1：gw_config_center —— 配置中心 / 注册中心连接
--   dashboard 里维护 Consul / Nacos 连接；添加路由时从注册中心拉服务列表。
--   v1 只实现 Consul 的服务发现（GwDiscoveryService），Nacos 留扩展点。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_config_center` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `center_code` VARCHAR(32)  NOT NULL COMMENT '连接编码，如 consul-local',
    `center_name` VARCHAR(64)  NOT NULL COMMENT '显示名，如「本地 Consul」',
    `center_type` VARCHAR(16)  NOT NULL COMMENT '类型：CONSUL / NACOS',
    `host`        VARCHAR(128) NOT NULL COMMENT '主机地址，如 127.0.0.1',
    `port`        INT          NOT NULL COMMENT '端口，Consul 默认 8500，Nacos 默认 8848',
    `acl_token`   VARCHAR(255) NULL     COMMENT 'Consul ACL Token（无 ACL 时留空）',
    `namespace`   VARCHAR(128) NULL     COMMENT 'Nacos namespace id；Consul 留空',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `remark`      VARCHAR(512) NULL     COMMENT '备注',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_center_code` (`center_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置中心/注册中心连接（Consul/Nacos）';


-- ----------------------------------------------------------------
-- 表 2：gw_config_item —— 网关全局配置项（key-value，按环境隔离）
--   对应 application.properties 里除 routes 以外的全部内容：
--     server.port、spring.cloud.gateway.server.webflux.httpclient.*、
--     autumn.authn.*、autumn.authz.*、limit.autumn-gateway.*、CORS ...
--   routes / 断言 / 过滤器不进这张表，它们有独立表（gw_route / gw_route_predicate /
--   gw_route_filter / gw_global_filter）。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_config_item` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `profile_code` VARCHAR(32)  NOT NULL DEFAULT 'dev' COMMENT '环境编码：dev/test/prod；v1 单环境即 dev',
    `config_group` VARCHAR(32)  NOT NULL COMMENT '分组，页面按组折叠：SERVER / SCG / AUTHN / AUTHZ / LIMIT / CORS / CUSTOM',
    `config_key`   VARCHAR(128) NOT NULL COMMENT '配置 key，如 autumn.authz.jwt.secret / server.port',
    `config_value` TEXT         NULL     COMMENT '配置值；is_encrypted=1 时存密文，页面掩码显示',
    `value_type`   VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '值类型，决定前端控件：STRING=输入框 INT/LONG=数字 BOOL=开关 DURATION=时长 LIST=多行标签 JSON=多行 TEXTAREA=大段文本',
    `is_required`  TINYINT      NOT NULL DEFAULT 0 COMMENT '1=必填，保存时校验，空值拦截',
    `is_encrypted` TINYINT      NOT NULL DEFAULT 0 COMMENT '1=敏感值（jwt.secret/密码），落库前加密、页面不回显明文（当前版本仅掩码，加密算法后补）',
    `sort_num`     INT          NOT NULL DEFAULT 0 COMMENT '同组内排序，小者靠前',
    `remark`       VARCHAR(512) NULL     COMMENT '该项说明，页面 tooltip 用',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_item_profile_key` (`profile_code`, `config_key`),
    KEY `idx_gw_item_group` (`profile_code`, `config_group`, `sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关全局配置项（key-value，按环境隔离）';


-- ----------------------------------------------------------------
-- 表 3：gw_route —— 路由（每个下游服务一条）
--   uri 有两种写法：
--     lb://服务名       走注册中心（从 gw_config_center 选服务后自动填）；
--     http://host:port  直连（没有注册中心时手填兜底）。
--   环境差异由注册中心解决，故路由不按 profile 拆。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_route` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_code`    VARCHAR(64)  NOT NULL COMMENT 'SCG route id，全局唯一，如 order-service',
    `route_name`    VARCHAR(128) NOT NULL COMMENT '显示名，如「订单服务」',
    `center_id`     BIGINT       NULL     COMMENT '关联 gw_config_center.id；为空=纯手填直连',
    `namespace`     VARCHAR(128) NULL     COMMENT '命名空间/分组（Nacos 用；Consul 留空）',
    `service_name`  VARCHAR(128) NULL     COMMENT '从注册中心选的服务名；选中后 uri 自动拼成 lb://service_name',
    `uri`           VARCHAR(512) NULL     COMMENT '最终 uri：lb://service_name 或 http://host:port；应用层校验非空',
    `order_num`     INT          NOT NULL DEFAULT 0 COMMENT '路由匹配顺序，越小越先匹配',
    `metadata_json` TEXT         NULL     COMMENT '路由元数据 Map，如 {"response-timeout":"3000"} 单路由覆盖超时',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '软删：1=已删；恢复=UPDATE 复活',
    `remark`        VARCHAR(512) NULL     COMMENT '备注',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_route_code` (`route_code`),
    KEY `idx_gw_route_center` (`center_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关路由（每个下游服务一条）';


-- ----------------------------------------------------------------
-- 表 4：gw_route_predicate —— 路由断言（1 路由 : N，断言之间 AND）
--   Path / Method / Host / Header / Query / Cookie / After / Before / Between / RemoteAddr / Weight
--   参数键值由 gw_predicate_template 定义，前端按模板渲染表单并做必填校验。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_route_predicate` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_id`       BIGINT       NOT NULL COMMENT '所属路由 gw_route.id',
    `predicate_name` VARCHAR(64)  NOT NULL COMMENT '断言工厂名：Path/Method/Host/Header/Query/Cookie/After/Before/Between/RemoteAddr/Weight',
    `args_json`      TEXT         NULL     COMMENT '参数键值对 JSON，如 {"pattern":"/api/order/**"}；key 来自模板',
    `sort_num`       INT          NOT NULL DEFAULT 0 COMMENT '页面展示顺序',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `del_flag`       TINYINT      NOT NULL DEFAULT 0 COMMENT '软删：1=已删',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_gw_pred_route` (`route_id`, `sort_num`),
    CONSTRAINT `fk_gw_pred_route` FOREIGN KEY (`route_id`) REFERENCES `gw_route` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路由断言';


-- ----------------------------------------------------------------
-- 表 5：gw_route_filter —— 路由级过滤器（1 路由 : N，sort_num 即链顺序）
--   每个下游服务可独立配置自己的过滤器（需求第 6 条）：只要不是全局 filter，
--   都能挂到任意路由上。
--   排序由 sort_num 控制，小者先执行，页面可上/下调序（需求第 7 条）。
--   「每路由只准挂一次」由 gw_filter_template.singleton_per_route 约束，
--   在应用层校验（添加过滤器时检查），不在 DB 层加唯一键（同一过滤器可能
--   允许挂多次，如 AddRequestHeader）。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_route_filter` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_id`    BIGINT       NOT NULL COMMENT '所属路由 gw_route.id',
    `filter_name` VARCHAR(64)  NOT NULL COMMENT '过滤器短名：Authz/StripPrefix/AddRequestHeader/RewritePath/CircuitBreaker/Retry/...',
    `args_json`   TEXT         NULL     COMMENT '参数键值对 JSON，如 {"mode":"authorize","appCode":"order"}；key 来自模板',
    `sort_num`    INT          NOT NULL DEFAULT 0 COMMENT '链内顺序，小者先执行',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '软删：1=已删',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_gw_rf_route` (`route_id`, `sort_num`),
    KEY `idx_gw_rf_name`  (`filter_name`),
    CONSTRAINT `fk_gw_rf_route` FOREIGN KEY (`route_id`) REFERENCES `gw_route` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路由级过滤器（带顺序）';


-- ----------------------------------------------------------------
-- 表 6：gw_global_filter —— 全局默认过滤器 default-filters
--   自动挂到每条路由上：统一响应头、统一限流默认值等。
--   与 gw_route_filter 的区别：这里是全局生效，路由级只在单条路由生效。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_global_filter` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `filter_name` VARCHAR(64)  NOT NULL COMMENT '工厂名，如 AddResponseHeader / RequestRateLimiter',
    `args_json`   TEXT         NULL     COMMENT '参数 JSON',
    `sort_num`    INT          NOT NULL DEFAULT 0 COMMENT 'default-filters 链内顺序，小者先执行',
    `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用（保留记录但不导出）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=删除预留',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '软删：1=已删',
    `remark`      VARCHAR(512) NULL     COMMENT '备注',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_gw_gf_sort` (`sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局默认过滤器 default-filters（带顺序）';


-- ----------------------------------------------------------------
-- 表 7：gw_filter_template —— 过滤器模板（参数表单定义）
--   args_schema_json 是参数表单定义数组，前端据此动态渲染、必填校验：
--   [
--     {"key":"mode","label":"模式","type":"enum","required":true,"options":["authenticate","authorize"],"default":"authenticate"},
--     {"key":"appCode","label":"应用编码","type":"string","required":true}
--   ]
--   字段说明：key=参数名  label=中文名
--            type=单键值(string/int/long/bool/enum/textarea/sensitive) 或多值 list
--            required=true 必填  options=enum 候选项  default=默认值  placeholder=占位提示
--   list 多值参数（如 Path.patterns / Method.methods / Header 多模式）：
--     {"key":"methods","label":"HTTP 方法","type":"list","itemType":"string","required":true}
--     前端渲染为可增删的动态行；导出时 list 拼成逗号分隔字符串（SCG 参数 Map<String,String> 限制）。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_filter_template` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `filter_name`         VARCHAR(64)  NOT NULL COMMENT '过滤器工厂名（唯一），如 Authz / StripPrefix',
    `display_name`        VARCHAR(64)  NOT NULL COMMENT '页面显示名，如「网关鉴权」',
    `scope`               VARCHAR(16)  NOT NULL DEFAULT 'ROUTE' COMMENT '适用范围：ROUTE=仅路由 GLOBAL=仅全局 BOTH=两者皆可',
    `singleton_per_route` TINYINT      NOT NULL DEFAULT 0 COMMENT '1=同一路由最多挂一次（一次）；0=可挂多次（多次）',
    `args_schema_json`    TEXT         NULL     COMMENT '参数表单定义数组（JSON），前端据此动态渲染；每项 type=单值(string/int/long/bool/enum/textarea/sensitive) 或 list(多值,配 itemType)',
    `status`              TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用（停用后添加时不再列出）',
    `remark`              VARCHAR(512) NULL     COMMENT '备注',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_filter_tpl` (`filter_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='过滤器模板（参数表单定义）';


-- ----------------------------------------------------------------
-- 表 8：gw_predicate_template —— 断言模板（参数表单定义）
--   args_schema_json 结构同 gw_filter_template，只是没有 scope / singleton 概念。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_predicate_template` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `predicate_name`    VARCHAR(64)  NOT NULL COMMENT '断言工厂名（唯一），如 Path / Method / Header',
    `display_name`      VARCHAR(64)  NOT NULL COMMENT '页面显示名，如「路径匹配」',
    `args_schema_json`  TEXT         NULL     COMMENT '参数表单定义数组（JSON），结构同 gw_filter_template（type 支持单值 + list/itemType）',
    `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `remark`            VARCHAR(512) NULL     COMMENT '备注',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_predicate_tpl` (`predicate_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='断言模板（参数表单定义）';


-- 自检（只读，确认 8 张表均存在）
SELECT 'gw_config_center'       AS table_name, COUNT(*) AS rows_cnt FROM `gw_config_center`
UNION ALL SELECT 'gw_config_item',       COUNT(*) FROM `gw_config_item`
UNION ALL SELECT 'gw_route',             COUNT(*) FROM `gw_route`
UNION ALL SELECT 'gw_route_predicate',   COUNT(*) FROM `gw_route_predicate`
UNION ALL SELECT 'gw_route_filter',      COUNT(*) FROM `gw_route_filter`
UNION ALL SELECT 'gw_global_filter',     COUNT(*) FROM `gw_global_filter`
UNION ALL SELECT 'gw_filter_template',   COUNT(*) FROM `gw_filter_template`
UNION ALL SELECT 'gw_predicate_template',COUNT(*) FROM `gw_predicate_template`;
