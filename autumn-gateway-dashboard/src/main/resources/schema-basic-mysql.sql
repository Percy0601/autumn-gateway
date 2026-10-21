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
    `code`        VARCHAR(64) NOT NULL COMMENT '命名空间编码，如 default',
    `name`        VARCHAR(64) NOT NULL COMMENT '命名空间名称',
    `description` VARCHAR(255) DEFAULT NULL,
    `status`      TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
    `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_namespace_code` (`code`),
    KEY `idx_namespace_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='命名空间（租户）';


-- basic_app 应用（多应用隔离）pwd
CREATE TABLE IF NOT EXISTS `basic_app` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `app_code`      VARCHAR(64) NOT NULL COMMENT '短标识，如 order、crm、admin',
    `name`          VARCHAR(64) NOT NULL,
    `base_path`     VARCHAR(128) DEFAULT NULL COMMENT '该应用 API 统一前缀，如 /api/order',
    `description`   VARCHAR(255) DEFAULT NULL,
    `status`        TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
    `namespace_id`  BIGINT DEFAULT NULL COMMENT '所属命名空间ID，关联 basic_namespace.id',
    `namespace_code` VARCHAR(64) DEFAULT NULL COMMENT '所属命名空间编码',
    `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_appid` (`app_code`),
    KEY `idx_status` (`status`),
    KEY `idx_app_namespace` (`namespace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用（多应用隔离）';


-- basic_group 用户岗位/职位/Title（岗位归属命名空间 basic_namespace，按公司/企业隔离）
CREATE TABLE IF NOT EXISTS `basic_group` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `namespace_id`  BIGINT DEFAULT NULL COMMENT '所属命名空间ID，关联 basic_namespace.id',
    `namespace_code` VARCHAR(64) DEFAULT NULL COMMENT '所属命名空间编码（冗余存储）',
    `name`        VARCHAR(100) NOT NULL COMMENT '岗位名称，职位名称，title',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     INT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    `code`        VARCHAR(100) NOT NULL DEFAULT '' COMMENT '岗位编码',
    `org_id`      BIGINT NOT NULL DEFAULT 0 COMMENT '部门名称',
    `job_id`      BIGINT NOT NULL DEFAULT 0 COMMENT '岗位ID',
    `status`      INT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-停用',
    PRIMARY KEY (`id`),
    KEY `basic_user_group_name_IDX` (`name`),
    KEY `basic_group_org_id_IDX` (`org_id`),
    KEY `basic_group_namespace_id_IDX` (`namespace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户岗位/职位/Title';


-- basic_group_group 用户组与用户组关联表（组层级：上级组 - 下级组）
CREATE TABLE IF NOT EXISTS `basic_group_group` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `parent_group_id` BIGINT NOT NULL DEFAULT 0 COMMENT '上级用户组ID',
    `group_id`        BIGINT NOT NULL DEFAULT 0 COMMENT '下级用户组ID',
    `deleted`         INT NOT NULL DEFAULT 0 COMMENT '删除状态：0-否 1-是',
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `op_user_id`      BIGINT NOT NULL DEFAULT 0 COMMENT '操作人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_group` (`parent_group_id`, `group_id`),
    KEY `idx_group_group_parent` (`parent_group_id`),
    KEY `idx_group_group_child` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组与用户组关联表（组层级）';


-- basic_menu_resource 菜单与资源关联表（一个菜单下挂哪些接口资源）
CREATE TABLE IF NOT EXISTS `basic_menu_resource` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `menu_id`     BIGINT NOT NULL DEFAULT 0 COMMENT '菜单ID',
    `resource_id` BIGINT NOT NULL DEFAULT 0 COMMENT '资源ID',
    `deleted`     INT NOT NULL DEFAULT 0 COMMENT '删除状态：0-否 1-是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `op_user_id`  BIGINT NOT NULL DEFAULT 0 COMMENT '操作人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_menu_resource` (`menu_id`, `resource_id`),
    KEY `idx_menu_resource_menu` (`menu_id`),
    KEY `idx_menu_resource_res` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单与资源关联表';


-- basic_menu 菜单
CREATE TABLE IF NOT EXISTS `basic_menu` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `app_id`              BIGINT NOT NULL DEFAULT 0 COMMENT '应用ID，关联 basic_app.id',
    `parent_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父级ID',
    `menu_type`           TINYINT NOT NULL DEFAULT 0 COMMENT '权限类型：1-目录 2-菜单 3-按钮',
    `menu_name`           VARCHAR(50) NOT NULL DEFAULT '' COMMENT '菜单名称',
    `menu_url`            VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '接口url（多个采用英文逗号分隔）',
    `menu_identification` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '菜单或按钮标识',
    `menu_icon`           VARCHAR(100) NOT NULL DEFAULT '' COMMENT '菜单图标',
    `sort_num`            INT NOT NULL DEFAULT 0 COMMENT '菜单排序',
    `menu_status`         TINYINT NOT NULL DEFAULT 1 COMMENT '菜单状态：1-启用 2-停用',
    `del_flag`            TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    `create_user`         BIGINT NOT NULL DEFAULT 0 COMMENT '创建人id',
    `create_time`         DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '创建时间',
    `update_user`         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '更新人id',
    `update_time`         DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '更新时间',
    `remark`              VARCHAR(200) NOT NULL DEFAULT '' COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_menu_app_id` (`app_id`),
    KEY `idx_menu_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单';


-- basic_oper_log 操作日志记录
CREATE TABLE IF NOT EXISTS `basic_oper_log` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `app_code`      VARCHAR(50) NOT NULL DEFAULT '' COMMENT '系统标识',
    `business_type` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务类型',
    `title`         VARCHAR(100) NOT NULL DEFAULT '' COMMENT '请求标题',
    `request_id`    VARCHAR(50) NOT NULL DEFAULT '' COMMENT '请求ID',
    `request_url`   VARCHAR(200) NOT NULL DEFAULT '' COMMENT '请求URL',
    `request_method` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '请求方法',
    `request_data`  VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '请求数据',
    `class_method`  VARCHAR(200) NOT NULL DEFAULT '' COMMENT '执行方法',
    `consume_time`  INT NOT NULL DEFAULT 0 COMMENT '执行时间（毫秒）',
    `response_data` VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '响应结果',
    `status`        TINYINT NOT NULL DEFAULT 0 COMMENT '操作状态：0-正常 1-异常',
    `error_msg`     VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '操作错误消息',
    `oper_id`       BIGINT NOT NULL DEFAULT 0 COMMENT '操作人',
    `oper_ip`       VARCHAR(50) NOT NULL DEFAULT '' COMMENT '操作人IP',
    `op_time`       DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_oper_log_op_time` (`op_time`),
    KEY `idx_oper_log_oper_id` (`oper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志记录';


-- basic_resource 权限原子项（合并了 Resource 的 URL 四元组）
CREATE TABLE IF NOT EXISTS `basic_resource` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `app_id`      BIGINT NOT NULL,
    `app_code`    VARCHAR(64) NOT NULL,
    `name`        VARCHAR(64) DEFAULT NULL COMMENT '权限名称，如"创建订单"',
    `path`        VARCHAR(255) DEFAULT NULL COMMENT 'URL 模式，如 /api/order/:id；MENU 时为路由路径',
    `http_method` VARCHAR(64) DEFAULT 'ALL' COMMENT 'HTTP 方法，支持多选逗号分隔，如 GET,POST；ALL 表示全部',
    `match_type`  VARCHAR(16) DEFAULT 'exact' COMMENT 'exact / prefix / suffix',
    `description` VARCHAR(255) DEFAULT NULL,
    `status`      TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
    `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_resource_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限原子项（合并了 Resource 的 URL 四元组）';


-- basic_role 角色表（角色可跨应用，归属命名空间 basic_namespace）
CREATE TABLE IF NOT EXISTS `basic_role` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `namespace_id`    BIGINT NOT NULL DEFAULT 0 COMMENT '命名空间ID，关联 basic_namespace.id',
    `namespace_code`  VARCHAR(64) NOT NULL DEFAULT '' COMMENT '命名空间编码',
    `code`            VARCHAR(50) NOT NULL DEFAULT '' COMMENT '角色编码',
    `name`            VARCHAR(50) NOT NULL DEFAULT '' COMMENT '角色名',
    `type`            TINYINT NOT NULL DEFAULT 0 COMMENT '角色类型：1-系统角色 2-自定义角色',
    `status`          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 2-停用',
    `del_flag`        TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    `create_user`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人id',
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '更新人id',
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark`          VARCHAR(50) NOT NULL DEFAULT '' COMMENT '备注',
    `master`          INT NOT NULL DEFAULT 2 COMMENT '是否是管理员: 1-管理员；2-普通',
    PRIMARY KEY (`id`),
    KEY `idx_role_namespace_id` (`namespace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';


-- basic_role_group 用户组与角色关联表
CREATE TABLE IF NOT EXISTS `basic_role_group` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `role_id`     BIGINT NOT NULL DEFAULT 0 COMMENT '角色ID',
    `group_id`    BIGINT NOT NULL DEFAULT 0 COMMENT '用户组ID',
    `deleted`     INT NOT NULL DEFAULT 0 COMMENT '删除状态：0-否 1-是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `op_user_id`  BIGINT NOT NULL DEFAULT 0 COMMENT '操作人',
    PRIMARY KEY (`id`),
    KEY `basic_role_group_role_id_IDX` (`role_id`),
    KEY `basic_role_group_group_id_IDX` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组与角色关联表';


-- basic_role_permission 角色权限关联表
CREATE TABLE IF NOT EXISTS `basic_role_permission` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `role_id`     BIGINT NOT NULL COMMENT '角色Id',
    `ref_id`      BIGINT NOT NULL COMMENT 'data_scope_id/menu_id',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`    INT NOT NULL DEFAULT 0 COMMENT '删除状态: 0:否，1：是',
    `op_user_id`  BIGINT NOT NULL DEFAULT 0 COMMENT '操作人Id',
    `type`        INT NOT NULL DEFAULT 1 COMMENT '权限类型：1-菜单权限；2-资源；3-数据权限',
    PRIMARY KEY (`id`),
    KEY `basic_role_permission_role_IDX` (`role_id`, `ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';


-- basic_user 基础用户表
-- 已整合旧 user 主表字段：uuid / username / nickname / avatar / email / phone /
-- status / password_updated_at / last_login_at，旧 user 表不再使用。
-- 命名沿用 basic_* 规范（create_time / update_time 对应旧表的 created_at / updated_at）。
CREATE TABLE IF NOT EXISTS `basic_user` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `uuid`                VARCHAR(36) DEFAULT NULL COMMENT '对外统一标识（OIDC 的 sub）：生成后永不变化，避免暴露自增ID、便于跨库合并；唯一，可为 NULL',
    `username`            VARCHAR(64) DEFAULT NULL COMMENT '登录名（工号语义）：创建后禁止修改，如需变更只能后台改库；唯一，可为 NULL',
    `nickname`            VARCHAR(64) NOT NULL DEFAULT '' COMMENT '昵称/姓名',
    `avatar`              VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像地址',
    `email`               VARCHAR(128) NOT NULL DEFAULT '' COMMENT '邮箱',
    `phone`               VARCHAR(32) NOT NULL DEFAULT '' COMMENT '手机号',
    `emp_no`              VARCHAR(50) NOT NULL DEFAULT '' COMMENT 'ESB人员工号',
    `admin`               INT NOT NULL DEFAULT 0 COMMENT '是否是管理员：1-是；0：否',
    `status`              TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0=禁用',
    `password`            VARCHAR(100) NOT NULL DEFAULT '' COMMENT '登录密码（BCrypt 哈希），参考 sys_user 方式',
    `login_error_count`   INT NOT NULL DEFAULT 5 COMMENT '剩余登录重试次数：每次密码错误 -1，减到 0 自动禁用账号；登录成功 / 重置密码 / 管理员启用时恢复为 5',
    `latest_role_id`      BIGINT NOT NULL DEFAULT 0 COMMENT '最近登录使用角色Id',
    `password_updated_at` DATETIME DEFAULT NULL COMMENT '密码最后修改时间，用于密码有效期/强制改密',
    `last_login_at`       DATETIME DEFAULT NULL COMMENT '最近登录时间',
    `create_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_basic_user_uuid` (`uuid`),
    UNIQUE KEY `uk_basic_user_username` (`username`),
    KEY `user_emp_no_IDX` (`emp_no`),
    KEY `idx_basic_user_email` (`email`),
    KEY `idx_basic_user_phone` (`phone`),
    KEY `idx_basic_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础用户表（已整合旧 user 主表字段）';


-- basic_user_access_role 用户最后登录角色，解决多端登录
CREATE TABLE IF NOT EXISTS `basic_user_access_role` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`        BIGINT NOT NULL DEFAULT 0 COMMENT '用户Id',
    `latest_role_id` BIGINT NOT NULL DEFAULT 0 COMMENT '最近登录使用角色Id',
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `emp_id`         VARCHAR(50) NOT NULL DEFAULT '' COMMENT 'jwt头部32位，用于索引。',
    `del_flag`       INT NOT NULL DEFAULT 0 COMMENT '删除标志：0-否，1-是',
    `access_time`    BIGINT NOT NULL DEFAULT 0 COMMENT '登录系统时间，就是生成jwt的create_time',
    PRIMARY KEY (`id`),
    KEY `basic_user_jwt_role_user_id_IDX` (`user_id`),
    KEY `basic_user_jwt_role_jwt_short_IDX` (`emp_id`),
    KEY `basic_user_access_role_update_time_IDX` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户最后登录角色，解决多端登录';


-- basic_user_group 员工岗位关联表
CREATE TABLE IF NOT EXISTS `basic_user_group` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `deleted`     INT NOT NULL DEFAULT 0 COMMENT '删除：0-否 1-是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `user_id`     BIGINT NOT NULL DEFAULT 0 COMMENT '用户Id',
    `group_id`    BIGINT NOT NULL DEFAULT 0 COMMENT '岗位Id',
    PRIMARY KEY (`id`),
    KEY `idx_user_group_user_id` (`user_id`),
    KEY `idx_user_group_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工岗位关联表';


-- basic_user_role 基础用户关联角色表
CREATE TABLE IF NOT EXISTS `basic_user_role` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `app_id`      BIGINT NOT NULL DEFAULT 0 COMMENT '应用ID，关联 basic_app.id',
    `user_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户ID',
    `role_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '角色ID',
    `del_flag`    TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    `create_user` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人id',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '更新人id',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark`      VARCHAR(200) NOT NULL DEFAULT '' COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `basic_user_role_user_id_IDX` (`user_id`),
    KEY `basic_user_role_role_id_IDX` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础用户关联角色表';
