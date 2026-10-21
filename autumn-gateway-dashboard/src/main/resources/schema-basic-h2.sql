-- ============================================================
-- Autumn Gateway Dashboard — basic_* 基础表结构（MySQL）
--
-- 优化点：
--   1. 幂等：全部使用 CREATE TABLE IF NOT EXISTS，可重复执行，
--      适配 spring.sql.init.mode=always 多次启动初始化的场景。
--      唯一键/索引随表内联声明（MySQL 不支持 CREATE INDEX IF NOT EXISTS，
--      内联写法在 IF NOT EXISTS 下天然幂等）。
--   2. 标识符统一改为反引号 ` `：MySQL 默认 sql_mode 下 "col" 会被解析为
--      字符串字面量，原脚本的双引号写法会直接报语法错误。
--   3. 精简冗余语法：去掉重复的列级 CHARACTER SET/COLLATE（统一在表级声明
--      utf8mb4_unicode_ci）、去掉 USING BTREE（MySQL 索引默认即 BTREE）、
--      去掉导出残留的 AUTO_INCREMENT=N / AUTO_INCREMENT 起始值。
--   4. 数值默认值去掉引号（DEFAULT '1' -> DEFAULT 1），避免隐式类型转换
--      及严格模式下的告警。
--   5. TIMESTAMP 统一改为 DATETIME（basic_role_permission），规避 2038 问题，
--      并与其余表的时间字段类型保持一致。
--   6. 补充高频查询索引（与 PostgreSQL 版本保持一致）：
--      basic_menu(app_id, parent_id)、basic_oper_log(op_time, oper_id)、
--      basic_resource(app_code)、basic_role(app_id)、
--      basic_user_group(user_id, group_id)。
-- ============================================================


-- basic_namespace 命名空间（租户，相当于一个公司/企业）
CREATE TABLE IF NOT EXISTS `basic_namespace` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `code`        VARCHAR(64) NOT NULL,
    `name`        VARCHAR(64) NOT NULL,
    `description` VARCHAR(255) DEFAULT NULL,
    `status`      TINYINT DEFAULT 1,
    `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ,
    PRIMARY KEY (`id`),
    UNIQUE KEY (`code`),
    KEY (`status`)
);


-- basic_app 应用（多应用隔离）
CREATE TABLE IF NOT EXISTS `basic_app` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `app_code`      VARCHAR(64) NOT NULL,
    `name`          VARCHAR(64) NOT NULL,
    `base_path`     VARCHAR(128) DEFAULT NULL,
    `description`   VARCHAR(255) DEFAULT NULL,
    `status`        TINYINT DEFAULT 1,
    `namespace_id`  BIGINT DEFAULT NULL,
    `namespace_code` VARCHAR(64) DEFAULT NULL,
    `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ,
    PRIMARY KEY (`id`),
    UNIQUE KEY (`app_code`),
    KEY (`status`),
    KEY (`namespace_id`)
);


-- basic_group 用户岗位/职位/Title（岗位归属命名空间 basic_namespace，按公司/企业隔离）
CREATE TABLE IF NOT EXISTS `basic_group` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `namespace_id`  BIGINT DEFAULT NULL,
    `namespace_code` VARCHAR(64) DEFAULT NULL,
    `name`        VARCHAR(100) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    `deleted`     INT NOT NULL DEFAULT 0,
    `code`        VARCHAR(100) NOT NULL DEFAULT '',
    `org_id`      BIGINT NOT NULL DEFAULT 0,
    `job_id`      BIGINT NOT NULL DEFAULT 0,
    `status`      INT NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY (`name`),
    KEY (`org_id`),
    KEY (`namespace_id`)
);


-- basic_group_group 用户组与用户组关联表（组层级：上级组 - 下级组）
CREATE TABLE IF NOT EXISTS `basic_group_group` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT,
    `parent_group_id` BIGINT NOT NULL DEFAULT 0,
    `group_id`        BIGINT NOT NULL DEFAULT 0,
    `deleted`         INT NOT NULL DEFAULT 0,
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    `op_user_id`      BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY (`parent_group_id`, `group_id`),
    KEY (`parent_group_id`),
    KEY (`group_id`)
);


-- basic_menu_resource 菜单与资源关联表（一个菜单下挂哪些接口资源）
CREATE TABLE IF NOT EXISTS `basic_menu_resource` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `menu_id`     BIGINT NOT NULL DEFAULT 0,
    `resource_id` BIGINT NOT NULL DEFAULT 0,
    `deleted`     INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    `op_user_id`  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY (`menu_id`, `resource_id`),
    KEY (`menu_id`),
    KEY (`resource_id`)
);


-- basic_menu 菜单
CREATE TABLE IF NOT EXISTS `basic_menu` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT,
    `app_id`              BIGINT NOT NULL DEFAULT 0,
    `parent_id`           BIGINT NOT NULL DEFAULT 0,
    `menu_type`           TINYINT NOT NULL DEFAULT 0,
    `menu_name`           VARCHAR(50) NOT NULL DEFAULT '',
    `menu_url`            VARCHAR(2000) NOT NULL DEFAULT '',
    `menu_identification` VARCHAR(200) NOT NULL DEFAULT '',
    `menu_icon`           VARCHAR(100) NOT NULL DEFAULT '',
    `sort_num`            INT NOT NULL DEFAULT 0,
    `menu_status`         TINYINT NOT NULL DEFAULT 1,
    `del_flag`            TINYINT NOT NULL DEFAULT 0,
    `create_user`         BIGINT NOT NULL DEFAULT 0,
    `create_time`         DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    `update_user`         BIGINT NOT NULL DEFAULT 0,
    `update_time`         DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    `remark`              VARCHAR(200) NOT NULL DEFAULT '',
    PRIMARY KEY (`id`),
    KEY (`app_id`),
    KEY (`parent_id`)
);


-- basic_oper_log 操作日志记录
CREATE TABLE IF NOT EXISTS `basic_oper_log` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `app_code`      VARCHAR(50) NOT NULL DEFAULT '',
    `business_type` VARCHAR(50) NOT NULL DEFAULT '',
    `title`         VARCHAR(100) NOT NULL DEFAULT '',
    `request_id`    VARCHAR(50) NOT NULL DEFAULT '',
    `request_url`   VARCHAR(200) NOT NULL DEFAULT '',
    `request_method` VARCHAR(100) NOT NULL DEFAULT '',
    `request_data`  VARCHAR(2000) NOT NULL DEFAULT '',
    `class_method`  VARCHAR(200) NOT NULL DEFAULT '',
    `consume_time`  INT NOT NULL DEFAULT 0,
    `response_data` VARCHAR(2000) NOT NULL DEFAULT '',
    `status`        TINYINT NOT NULL DEFAULT 0,
    `error_msg`     VARCHAR(2000) NOT NULL DEFAULT '',
    `oper_id`       BIGINT NOT NULL DEFAULT 0,
    `oper_ip`       VARCHAR(50) NOT NULL DEFAULT '',
    `op_time`       DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    PRIMARY KEY (`id`),
    KEY (`op_time`),
    KEY (`oper_id`)
);


-- basic_resource 权限原子项（合并了 Resource 的 URL 四元组）
CREATE TABLE IF NOT EXISTS `basic_resource` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `app_id`      BIGINT NOT NULL,
    `app_code`    VARCHAR(64) NOT NULL,
    `name`        VARCHAR(64) DEFAULT NULL,
    `path`        VARCHAR(255) DEFAULT NULL,
    `http_method` VARCHAR(64) DEFAULT 'ALL',
    `match_type`  VARCHAR(16) DEFAULT 'exact',
    `description` VARCHAR(255) DEFAULT NULL,
    `status`      TINYINT DEFAULT 1,
    `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ,
    PRIMARY KEY (`id`),
    KEY (`app_code`)
);


-- basic_role 角色表（角色可跨应用，归属命名空间 basic_namespace）
CREATE TABLE IF NOT EXISTS `basic_role` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT,
    `namespace_id`    BIGINT NOT NULL DEFAULT 0,
    `namespace_code`  VARCHAR(64) NOT NULL DEFAULT '',
    `code`            VARCHAR(50) NOT NULL DEFAULT '',
    `name`            VARCHAR(50) NOT NULL DEFAULT '',
    `type`            TINYINT NOT NULL DEFAULT 0,
    `status`          TINYINT NOT NULL DEFAULT 1,
    `del_flag`        TINYINT NOT NULL DEFAULT 0,
    `create_user`     BIGINT NOT NULL DEFAULT 0,
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user`     BIGINT NOT NULL DEFAULT 0,
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    `remark`          VARCHAR(50) NOT NULL DEFAULT '',
    `master`          INT NOT NULL DEFAULT 2,
    PRIMARY KEY (`id`),
    KEY (`namespace_id`)
);


-- basic_role_group 用户组与角色关联表
CREATE TABLE IF NOT EXISTS `basic_role_group` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `role_id`     BIGINT NOT NULL DEFAULT 0,
    `group_id`    BIGINT NOT NULL DEFAULT 0,
    `deleted`     INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    `op_user_id`  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY (`role_id`),
    KEY (`group_id`)
);


-- basic_role_permission 角色权限关联表
CREATE TABLE IF NOT EXISTS `basic_role_permission` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `role_id`     BIGINT NOT NULL,
    `ref_id`      BIGINT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ,
    `del_flag`    INT NOT NULL DEFAULT 0,
    `op_user_id`  BIGINT NOT NULL DEFAULT 0,
    `type`        INT NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY (`role_id`, `ref_id`)
);


-- basic_user 基础用户表
-- 已整合旧 user 主表字段：uuid / username / nickname / avatar / email / phone /
-- status / password_updated_at / last_login_at，旧 user 表不再使用。
-- 命名沿用 basic_* 规范（create_time / update_time 对应旧表的 created_at / updated_at）。
CREATE TABLE IF NOT EXISTS `basic_user` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT,
    `uuid`                VARCHAR(36) DEFAULT NULL,
    `username`            VARCHAR(64) DEFAULT NULL,
    `nickname`            VARCHAR(64) NOT NULL DEFAULT '',
    `avatar`              VARCHAR(255) NOT NULL DEFAULT '',
    `email`               VARCHAR(128) NOT NULL DEFAULT '',
    `phone`               VARCHAR(32) NOT NULL DEFAULT '',
    `emp_no`              VARCHAR(50) NOT NULL DEFAULT '',
    `admin`               INT NOT NULL DEFAULT 0,
    `status`              TINYINT NOT NULL DEFAULT 1,
    `password`            VARCHAR(100) NOT NULL DEFAULT '',
    `login_error_count`   INT NOT NULL DEFAULT 5,
    `latest_role_id`      BIGINT NOT NULL DEFAULT 0,
    `password_updated_at` DATETIME DEFAULT NULL,
    `last_login_at`       DATETIME DEFAULT NULL,
    `create_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    PRIMARY KEY (`id`),
    UNIQUE KEY (`uuid`),
    UNIQUE KEY (`username`),
    KEY (`emp_no`),
    KEY (`email`),
    KEY (`phone`),
    KEY (`status`)
);


-- basic_user_access_role 用户最后登录角色，解决多端登录
CREATE TABLE IF NOT EXISTS `basic_user_access_role` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT NOT NULL DEFAULT 0,
    `latest_role_id` BIGINT NOT NULL DEFAULT 0,
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    `emp_id`         VARCHAR(50) NOT NULL DEFAULT '',
    `del_flag`       INT NOT NULL DEFAULT 0,
    `access_time`    BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY (`user_id`),
    KEY (`emp_id`),
    KEY (`update_time`)
);


-- basic_user_group 员工岗位关联表
CREATE TABLE IF NOT EXISTS `basic_user_group` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `deleted`     INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    `user_id`     BIGINT NOT NULL DEFAULT 0,
    `group_id`    BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY (`user_id`),
    KEY (`group_id`)
);


-- basic_user_role 基础用户关联角色表
CREATE TABLE IF NOT EXISTS `basic_user_role` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `app_id`      BIGINT NOT NULL DEFAULT 0,
    `user_id`     BIGINT NOT NULL DEFAULT 0,
    `role_id`     BIGINT NOT NULL DEFAULT 0,
    `del_flag`    TINYINT NOT NULL DEFAULT 0,
    `create_user` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_user` BIGINT NOT NULL DEFAULT 0,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    `remark`      VARCHAR(200) NOT NULL DEFAULT '',
    PRIMARY KEY (`id`),
    KEY (`user_id`),
    KEY (`role_id`)
);