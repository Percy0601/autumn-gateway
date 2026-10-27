-- ================================================================
-- Autumn Gateway Dashboard — 网关可视化配置表结构（MySQL 8.0+）
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
-- 幂等：全部 CREATE TABLE IF NOT EXISTS；加索引用存储过程探测（与 basic_* 一致）。
-- ================================================================


-- ----------------------------------------------------------------
-- 表 1：gw_config_item —— 网关全局配置项（key-value，按环境隔离）
--
-- 这张表就是 application.properties 里除 routes 以外的全部内容：
--   server.port、spring.datasource.*、spring.cloud.gateway.httpclient.*、
--   autumn.authn.*、autumn.authz.*、limit.autumn-gateway.* ...
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_config_item` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `profile_code` VARCHAR(32)  NOT NULL DEFAULT 'dev' COMMENT '环境编码：dev/test/prod；v1 单环境即 dev',
    `config_group` VARCHAR(32)  NOT NULL COMMENT '分组，页面按组折叠：SERVER / DATASOURCE / SCG / AUTHN / AUTHZ / LIMIT / CORS / CUSTOM',
    `config_key`   VARCHAR(128) NOT NULL COMMENT '配置 key，如 autumn.authz.jwt.secret / server.port',
    `config_value` TEXT         NULL     COMMENT '配置值；is_encrypted=1 时存密文，页面掩码显示',
    `value_type`   VARCHAR(16)  NOT NULL DEFAULT 'STRING'
                   COMMENT '值类型，决定前端控件：STRING=输入框 INT/LONG=数字 BOOL=开关 DURATION=时长 LIST=多行标签 JSON=多行 TEXTAREA=大段SQL',
    `is_required`  TINYINT      NOT NULL DEFAULT 0 COMMENT '1=必填，空值拦截保存',
    `is_encrypted` TINYINT      NOT NULL DEFAULT 0 COMMENT '1=敏感值（jwt.secret/密码），落库前加密、页面不回显明文',
    `sort_num`     INT          NOT NULL DEFAULT 0 COMMENT '同组内排序，小者靠前',
    `remark`       VARCHAR(512) NULL     COMMENT '该项的说明，页面 tooltip 用',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_item_profile_key` (`profile_code`, `config_key`),
    KEY `idx_gw_item_group` (`profile_code`, `config_group`, `sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关全局配置项（key-value，按环境隔离）';


-- ----------------------------------------------------------------
-- 表 2：gw_route —— 路由（每个下游服务一条）
--
-- 对应 spring.cloud.gateway.routes[i]。uri 一律 lb://服务名 走注册中心，
-- 环境差异由注册中心解决，故路由不按 profile 拆。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_route` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_code`    VARCHAR(64)  NOT NULL COMMENT 'SCG route id，全局唯一，如 order-service',
    `route_name`    VARCHAR(128) NOT NULL COMMENT '显示名，如「订单服务」',
    `app_code`      VARCHAR(64)  NULL     COMMENT '绑定应用编码，关联 basic_app.app_code，可空',
    `uri`           VARCHAR(512) NOT NULL COMMENT 'lb://service-order 走注册中心；http://host:port 直连',
    `order_num`     INT          NOT NULL DEFAULT 0 COMMENT '路由匹配顺序，越小越先匹配',
    `metadata_json` TEXT         NULL     COMMENT '路由元数据 Map，如 {"response-timeout":"3000"} 单路由覆盖超时',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '软删：1=已删；恢复=UPDATE 复活',
    `remark`        VARCHAR(512) NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_route_code` (`route_code`),
    KEY `idx_gw_route_app` (`app_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关路由（每个下游服务一条）';


-- ----------------------------------------------------------------
-- 表 3：gw_route_predicate —— 路由断言（1 路由 : N，断言之间 AND）
--   Path/Method/Host/Header/Query/Cookie/After/Between/RemoteAddr/Weight
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_route_predicate` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_id`       BIGINT       NOT NULL COMMENT '所属路由 gw_route.id',
    `predicate_name` VARCHAR(64)  NOT NULL COMMENT '断言工厂名：Path/Method/Host/Header/Query/Cookie/After/Between/RemoteAddr/Weight',
    `args_json`      TEXT         NULL     COMMENT '参数 JSON，如 {"pattern":"/api/order/**"}',
    `sort_num`       INT          NOT NULL DEFAULT 0 COMMENT '页面展示顺序',
    `status`         TINYINT      NOT NULL DEFAULT 1,
    `del_flag`       TINYINT      NOT NULL DEFAULT 0,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_gw_pred_route` (`route_id`, `sort_num`),
    CONSTRAINT `fk_gw_pred_route` FOREIGN KEY (`route_id`) REFERENCES `gw_route` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路由断言';


-- ----------------------------------------------------------------
-- 表 4：gw_route_filter —— 路由过滤器（1 路由 : N，sort_num 即链顺序）
--   「每路由只准挂一次」由代码侧 singletonPerRoute=true 控制，不加 DB 唯一键
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `gw_route_filter` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_id`    BIGINT       NOT NULL COMMENT '所属路由 gw_route.id',
    `filter_name` VARCHAR(64)  NOT NULL COMMENT '过滤器短名：Authz/StripPrefix/AddRequestHeader/RewritePath/CircuitBreaker/Retry/...',
    `args_json`   TEXT         NULL     COMMENT '参数 JSON，如 {"mode":"authorize","appCode":"order"}',
    `sort_num`    INT          NOT NULL DEFAULT 0 COMMENT '链内顺序，小者先执行',
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `del_flag`    TINYINT      NOT NULL DEFAULT 0,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_gw_rf_route` (`route_id`, `sort_num`),
    KEY `idx_gw_rf_name`  (`filter_name`),
    CONSTRAINT `fk_gw_rf_route` FOREIGN KEY (`route_id`) REFERENCES `gw_route` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路由级过滤器（带顺序）';


-- ----------------------------------------------------------------
-- 表 5：gw_global_filter —— 全局默认过滤器 default-filters
--   自动挂到每条路由上：统一响应头、统一限流默认值等
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
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_gw_gf_sort` (`sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局默认过滤器 default-filters（带顺序）';


-- 自检
SELECT 'gw_config_item'     AS table_name, COUNT(*) AS rows_cnt FROM `gw_config_item`
UNION ALL SELECT 'gw_route',            COUNT(*) FROM `gw_route`
UNION ALL SELECT 'gw_route_predicate',  COUNT(*) FROM `gw_route_predicate`
UNION ALL SELECT 'gw_route_filter',     COUNT(*) FROM `gw_route_filter`
UNION ALL SELECT 'gw_global_filter',    COUNT(*) FROM `gw_global_filter`;
