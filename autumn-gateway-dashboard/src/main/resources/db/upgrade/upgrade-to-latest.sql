-- ============================================================
-- 唯一维护的存量库升级脚本（upgrade-to-latest）
--
-- 为什么只有一个文件：
--   以前是「每个版本一个 migration-vN-to-vN+1.sql」，文件越滚越多、还分 MySQL/H2 两份，
--   维护成本高、容易漏执行。现在改成「一个文件 + 分段 + 版本台账」：
--     · 每个升级动作是一段，用 `-- @@STEP <id> [<名称>] [<方言>]` 标记；
--     · 启动时由 SchemaUpgradeRunner 执行，并把「已执行过的段」记进 basic_schema_version 表；
--     · 台账里已有记录的段直接跳过 —— 这就是「不重复」；
--     · 每段内部又全部写成幂等语句（CREATE TABLE IF NOT EXISTS / INSERT ... WHERE NOT EXISTS），
--       所以哪怕台账丢失、手工重复执行，也只是「没有变化」，不会插重数据 —— 这就是「不污染」。
--
-- 方言约定：
--   段标记末尾可带方言（mysql / h2 / postgresql），不带 = 三种方言通用。
--   执行器按当前数据源自动挑段，因此同一个文件可以同时服务 MySQL / H2 / PostgreSQL，
--   不需要再维护 -h2database 之类的平行副本。
--
-- 历史归档：
--   v1 → v8 的增量脚本已整体归档到 db/migration/archive/（只读，保留可追溯）。
--   仍停在 v8 之前的库，先按归档脚本逐级补齐到 v8，之后所有变更都只进本文件。
--
-- 新增一个升级动作的做法：
--   在本文件末尾追加一段，step id 递增（100、101、102 …），语句保持幂等。
--   不要修改已上线段的正文（台账已记录，改了也不会重跑）；要改就新加一段。
-- ============================================================


-- ============================================================
-- @@STEP 100 thrift-generic-tables
-- Thrift 泛化路由（HTTP → Thrift）两张表：
--   gw_thrift_service     服务端点 + IDL（相当于 ShenYu 的 metadata）
--   gw_thrift_conversion  入参/出参映射规则（相当于 ShenYu 的 param-mapping）
-- 与 db/gateway/gw_schema_{mysql,postgresql,h2}.sql 里的定义保持一致；
-- 这里再写一遍是为了「不重启、不依赖 spring.sql.init」也能把存量库补齐。
-- ============================================================

CREATE TABLE IF NOT EXISTS `gw_thrift_service` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `service_code`     VARCHAR(64)  NOT NULL COMMENT '服务编码：必须等于 IDL 里的 service 名，上送为 autumn.thrift.services[].name',
    `service_name`     VARCHAR(128) NOT NULL DEFAULT '' COMMENT '展示名（中文），仅页面展示用',
    `host`             VARCHAR(128) NOT NULL COMMENT 'thrift 服务端地址',
    `port`             INT          NOT NULL COMMENT 'thrift 服务端端口',
    `idl_content`      TEXT                  COMMENT '内联 IDL（与 idl_location 二选一）',
    `idl_location`     VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'IDL 位置：classpath:/xx.thrift 或 file:/xx.thrift',
    `framed`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否 framed 传输：1 是 0 否',
    `multiplexed`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否 multiplexed：1 是 0 否',
    `multiplexed_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'multiplexed 服务名',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    `del_flag`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未删除 1 已删除',
    `remark`           VARCHAR(512) NOT NULL DEFAULT '' COMMENT '备注',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_gw_thrift_service_code` UNIQUE (`service_code`)
) COMMENT='Thrift 服务（协议转换目标）';

CREATE TABLE IF NOT EXISTS `gw_thrift_conversion` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `conversion_code` VARCHAR(64)  NOT NULL COMMENT '规则名：路由过滤器第三参 / autumn.thrift.mappings[].name',
    `conversion_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '展示名',
    `service_code`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '关联 gw_thrift_service.service_code',
    `method_name`     VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'thrift 方法名；空=由路由/动态分发决定',
    `http_path`       VARCHAR(512) NOT NULL DEFAULT '' COMMENT '建议的 HTTP 路径（展示/联调参考）',
    `include_body`    TINYINT      NOT NULL DEFAULT 1 COMMENT '1=请求体打底 0=只看映射规则',
    `arguments_json`  TEXT                  COMMENT '入参规则 JSON：[{arg,source,key,value,required,defaultValue}]',
    `response_mode`   VARCHAR(16)  NOT NULL DEFAULT 'ENVELOPE' COMMENT '响应形态：ENVELOPE / DATA',
    `success_status`  INT          NOT NULL DEFAULT 0 COMMENT '成功时覆盖 HTTP 状态码；0=默认 200',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    `del_flag`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未删除 1 已删除',
    `remark`          VARCHAR(512) NOT NULL DEFAULT '' COMMENT '备注',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_gw_thrift_conversion_code` UNIQUE (`conversion_code`)
) COMMENT='Thrift 协议转换规则（HTTP 入参/出参映射）';


-- ============================================================
-- @@STEP 101 thrift-menu
-- 「网关配置 → 协议转换」菜单（basic_menu）。
-- 父目录按 menu_identification='gateway' 动态取，不写死 id（各库自增 id 不同）。
-- 幂等：已存在 gateway:thrift 就整条不进。
-- ============================================================

INSERT INTO `basic_menu`
(`app_id`, `parent_id`, `menu_type`, `menu_name`, `menu_url`, `menu_identification`,
 `menu_icon`, `sort_num`, `menu_status`, `del_flag`, `create_user`, `create_time`,
 `update_user`, `update_time`, `remark`)
SELECT p.`app_id`, p.`id`, 2, '协议转换', 'gateway/thrift/index', 'gateway:thrift',
       'swap', 8, 1, 0, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP,
       'HTTP endpoint → Thrift 泛化调用配置'
  FROM `basic_menu` p
 WHERE p.`menu_identification` = 'gateway'
   AND p.`del_flag` = 0
   AND NOT EXISTS (SELECT 1 FROM `basic_menu` t
                    WHERE t.`menu_identification` = 'gateway:thrift'
                      AND t.`del_flag` = 0);


-- ============================================================
-- @@STEP 102 thrift-menu-grant
-- 把新菜单授权给管理员角色（basic_role.master = 1，含 SUPER_ADMIN / admin）。
-- 说明：超管（master=1）本来就能看到全部菜单，这里显式授权是为了
--       「角色管理页里能看到这条授权」「导出权限时口径完整」，并非必需。
-- 幂等：同角色同菜单已有 type=1 授权就跳过。
-- ============================================================

INSERT INTO `basic_role_permission`
(`role_id`, `ref_id`, `type`, `del_flag`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, m.`id`, 1, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM `basic_role` r
  JOIN `basic_menu` m
    ON m.`menu_identification` = 'gateway:thrift'
   AND m.`del_flag` = 0
 WHERE r.`del_flag` = 0
   AND r.`master` = 1
   AND NOT EXISTS (SELECT 1 FROM `basic_role_permission` p
                    WHERE p.`role_id` = r.`id`
                      AND p.`ref_id` = m.`id`
                      AND p.`type` = 1
                      AND p.`del_flag` = 0);
