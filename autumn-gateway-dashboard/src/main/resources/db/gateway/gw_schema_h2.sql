-- ================================================================
-- Autumn Gateway Dashboard — 网关可视化配置表结构（H2 2.5.x，MODE=MySQL）
--
-- 来源：由 gw_schema_mysql.sql 适配而来，供 spring profile = h2database 使用。
-- 表结构、注释、索引与 MySQL 版完全对齐，仅做 H2 方言适配：
--   1) 去掉 MySQL 专属表选项 ENGINE / CHARSET / COLLATE，仅保留表注释；
--   2) 索引名带 gw_ 前缀（H2 的索引名是 SCHEMA 级全局唯一，MySQL 仅表内唯一）；
--   3) 时间字段统一 DATETIME（与 schema-h2database.sql 保持一致）。
--
-- 种子数据（常用过滤器/断言模板、常用配置项）由 Java 侧 GwDataInitializer 幂等播种，
--   不放在本脚本里，避免每次启动重复 INSERT。
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，可随 spring.sql.init.mode=always 重复执行。
-- ================================================================


-- 表 1：配置中心 / 注册中心连接（Consul / Nacos）
CREATE TABLE IF NOT EXISTS `gw_config_center` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `center_code` VARCHAR(32)  NOT NULL COMMENT '连接编码，如 consul-local',
    `center_name` VARCHAR(64)  NOT NULL COMMENT '显示名',
    `center_type` VARCHAR(16)  NOT NULL COMMENT '类型：CONSUL / NACOS',
    `host`        VARCHAR(128) NOT NULL COMMENT '主机地址',
    `port`        INT          NOT NULL COMMENT '端口，Consul 8500 / Nacos 8848',
    `acl_token`   VARCHAR(255) NULL     COMMENT 'Consul ACL Token（可空）',
    `namespace`   VARCHAR(128) NULL     COMMENT 'Nacos namespace id；Consul 留空',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `remark`      VARCHAR(512) NULL     COMMENT '备注',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_center_code` (`center_code`)
) COMMENT='配置中心/注册中心连接（Consul/Nacos）';


-- 表 2：网关全局配置项（key-value，按环境隔离）
CREATE TABLE IF NOT EXISTS `gw_config_item` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `profile_code` VARCHAR(32)  NOT NULL DEFAULT 'dev' COMMENT '环境编码：dev/test/prod',
    `config_group` VARCHAR(32)  NOT NULL COMMENT '分组：SERVER / SCG / AUTHN / AUTHZ / LIMIT / CORS / CUSTOM',
    `config_key`   VARCHAR(128) NOT NULL COMMENT '配置 key，如 autumn.authz.jwt.secret',
    `config_value` TEXT         NULL     COMMENT '配置值；is_encrypted=1 时页面掩码显示',
    `value_type`   VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT 'STRING/INT/LONG/BOOL/DURATION/LIST/JSON/TEXTAREA，决定前端控件',
    `is_required`  TINYINT      NOT NULL DEFAULT 0 COMMENT '1=必填，空值拦截',
    `is_encrypted` TINYINT      NOT NULL DEFAULT 0 COMMENT '1=敏感值，页面不回显明文',
    `sort_num`     INT          NOT NULL DEFAULT 0 COMMENT '同组内排序',
    `remark`       VARCHAR(512) NULL     COMMENT '说明，页面 tooltip 用',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_item_profile_key` (`profile_code`, `config_key`),
    INDEX `idx_gw_item_group` (`profile_code`, `config_group`, `sort_num`)
) COMMENT='网关全局配置项（key-value，按环境隔离）';


-- 表 3：路由（每个下游服务一条）
CREATE TABLE IF NOT EXISTS `gw_route` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_code`    VARCHAR(64)  NOT NULL COMMENT 'SCG route id，全局唯一',
    `route_name`    VARCHAR(128) NOT NULL COMMENT '显示名',
    `center_id`     BIGINT       NULL     COMMENT '关联 gw_config_center.id；为空=纯手填直连',
    `namespace`     VARCHAR(128) NULL     COMMENT '命名空间/分组（Nacos 用）',
    `service_name`  VARCHAR(128) NULL     COMMENT '从注册中心选的服务名；uri 自动拼 lb://service_name',
    `uri`           VARCHAR(512) NULL     COMMENT 'lb://service_name 或 http://host:port；应用层校验非空',
    `order_num`     INT          NOT NULL DEFAULT 0 COMMENT '路由匹配顺序，越小越先',
    `metadata_json` TEXT         NULL     COMMENT '路由元数据 Map',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '软删：1=已删',
    `remark`        VARCHAR(512) NULL     COMMENT '备注',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_route_code` (`route_code`),
    INDEX `idx_gw_route_center` (`center_id`, `status`)
) COMMENT='网关路由（每个下游服务一条）';


-- 表 4：路由断言（1 路由 : N，AND 关系）
CREATE TABLE IF NOT EXISTS `gw_route_predicate` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_id`       BIGINT       NOT NULL COMMENT '所属路由 gw_route.id',
    `predicate_name` VARCHAR(64)  NOT NULL COMMENT '断言工厂名：Path/Method/Host/Header/...',
    `args_json`      TEXT         NULL     COMMENT '参数键值对 JSON；key 来自模板',
    `sort_num`       INT          NOT NULL DEFAULT 0 COMMENT '页面展示顺序',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `del_flag`       TINYINT      NOT NULL DEFAULT 0 COMMENT '软删：1=已删',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_gw_pred_route` (`route_id`, `sort_num`),
    CONSTRAINT `fk_gw_pred_route` FOREIGN KEY (`route_id`) REFERENCES `gw_route` (`id`)
) COMMENT='路由断言';


-- 表 5：路由级过滤器（1 路由 : N，sort_num 即链顺序）
CREATE TABLE IF NOT EXISTS `gw_route_filter` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `route_id`    BIGINT       NOT NULL COMMENT '所属路由 gw_route.id',
    `filter_name` VARCHAR(64)  NOT NULL COMMENT '过滤器短名：Authz/StripPrefix/AddRequestHeader/...',
    `args_json`   TEXT         NULL     COMMENT '参数键值对 JSON；key 来自模板',
    `sort_num`    INT          NOT NULL DEFAULT 0 COMMENT '链内顺序，小者先执行',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '软删：1=已删',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_gw_rf_route` (`route_id`, `sort_num`),
    INDEX `idx_gw_rf_name`  (`filter_name`),
    CONSTRAINT `fk_gw_rf_route` FOREIGN KEY (`route_id`) REFERENCES `gw_route` (`id`)
) COMMENT='路由级过滤器（带顺序）';


-- 表 6：全局默认过滤器 default-filters
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
    INDEX `idx_gw_gf_sort` (`sort_num`)
) COMMENT='全局默认过滤器 default-filters（带顺序）';


-- 表 7：过滤器模板（参数表单定义）
CREATE TABLE IF NOT EXISTS `gw_filter_template` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `filter_name`         VARCHAR(64)  NOT NULL COMMENT '过滤器工厂名（唯一）',
    `display_name`        VARCHAR(64)  NOT NULL COMMENT '页面显示名',
    `scope`               VARCHAR(16)  NOT NULL DEFAULT 'ROUTE' COMMENT 'ROUTE=仅路由 GLOBAL=仅全局 BOTH=两者皆可',
    `singleton_per_route` TINYINT      NOT NULL DEFAULT 0 COMMENT '1=同一路由最多挂一次；0=可挂多次',
    `args_schema_json`    TEXT         NULL     COMMENT '参数表单定义数组（JSON）；每项 type=单值(string/int/long/bool/enum/textarea/sensitive) 或 list(多值,配 itemType),required/options/default/placeholder',
    `status`              TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `remark`              VARCHAR(512) NULL     COMMENT '备注',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_filter_tpl` (`filter_name`)
) COMMENT='过滤器模板（参数表单定义）';


-- 表 8：断言模板（参数表单定义）
CREATE TABLE IF NOT EXISTS `gw_predicate_template` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `predicate_name`    VARCHAR(64)  NOT NULL COMMENT '断言工厂名（唯一）',
    `display_name`      VARCHAR(64)  NOT NULL COMMENT '页面显示名',
    `args_schema_json`  TEXT         NULL     COMMENT '参数表单定义数组（JSON），结构同 gw_filter_template（type 支持单值 + list/itemType）',
    `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `remark`            VARCHAR(512) NULL     COMMENT '备注',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gw_predicate_tpl` (`predicate_name`)
) COMMENT='断言模板（参数表单定义）';
