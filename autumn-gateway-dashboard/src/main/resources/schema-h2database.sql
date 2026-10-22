-- ============================================================
-- Autumn Gateway Dashboard — H2 数据库表结构
--
-- 来源：由 schema-mysql.sql + schema-basic-mysql.sql 适配而来，
--       供 spring profile = h2database 使用（H2 2.5.x，MODE=MySQL）。
--
-- 与 MySQL 版本的关键差异（H2 限制，改动时务必注意）：
--   1. 索引名在 H2 中是 SCHEMA 级全局唯一，MySQL 中仅表内唯一。
--      原脚本多表重名（idx_status / idx_user / uk_appid）会直接报
--      "Index ... already exists"，因此统一加表名前缀：
--        idx_status  -> idx_app_status / idx_user_status / idx_perm_status /
--                       idx_role_status / idx_basic_app_status
--        idx_user    -> idx_uaa_user / idx_audit_user
--        uk_appid    -> uk_app_appid / uk_basic_app_code
--   2. 去掉 MySQL 专属表选项 ENGINE / CHARSET / COLLATE（H2 无存储引擎概念，
--      字符集固定 UTF-8），仅保留表注释。
--   3. 其余 MySQL 方言 H2 在 MODE=MySQL 下原生支持，故保持不变：
--      反引号标识符、`AUTO_INCREMENT`、`DATETIME ... ON UPDATE
--      CURRENT_TIMESTAMP`、列级 `COMMENT`、内联 `UNIQUE KEY` / `KEY` / `INDEX`、
--      `CREATE TABLE IF NOT EXISTS`（幂等，可随每次启动重复执行）。
-- ============================================================


-- ============================================================
-- 一、旧表结构（Wolf RBAC 模型）
-- ============================================================

-- 1. 应用（多应用隔离）
CREATE TABLE IF NOT EXISTS application (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  appid       VARCHAR(64) NOT NULL COMMENT '短标识，如 order、crm、admin',
  name        VARCHAR(64) NOT NULL COMMENT '应用名称',
  base_path   VARCHAR(128) COMMENT '该应用 API 统一前缀，如 /api/order',
  description VARCHAR(255) COMMENT '应用描述',
  status      TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  UNIQUE KEY uk_app_appid (appid),
  INDEX idx_app_status (status)
) COMMENT='应用（多应用隔离）';

-- 2. 用户（全局共享）
CREATE TABLE IF NOT EXISTS `user` (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  uuid          VARCHAR(36) NOT NULL COMMENT '对外统一标识（OIDC 的 sub）：生成后永不变化，避免暴露自增ID、便于跨库合并',
  username      VARCHAR(64) UNIQUE COMMENT '登录名（工号语义）：创建后禁止修改，如需变更只能后台改库',
  nickname      VARCHAR(64) COMMENT '昵称/姓名',
  avatar        VARCHAR(255) COMMENT '头像地址',
  email         VARCHAR(128) COMMENT '邮箱',
  phone         VARCHAR(32) COMMENT '手机号',
  emp_no        VARCHAR(64) COMMENT '员工工号',
  status        TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
  password_updated_at DATETIME COMMENT '密码最后修改时间，用于密码有效期/强制改密',
  last_login_at DATETIME COMMENT '最近登录时间',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  UNIQUE KEY uk_uuid (uuid),
  INDEX idx_user_email (email),
  INDEX idx_user_phone (phone),
  INDEX idx_user_status (status)
) COMMENT='用户主表（全局共享）';

-- 3. 用户在哪些应用下有身份（Wolf 核心设计）
CREATE TABLE IF NOT EXISTS user_app (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id    BIGINT NOT NULL COMMENT '用户ID，关联 user.id',
    app_id     BIGINT NOT NULL COMMENT '应用ID，关联 application.id',
    is_admin   TINYINT DEFAULT 0 COMMENT '是否该应用管理员',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_app (user_id, app_id),
    INDEX idx_app_user (app_id, user_id),
    FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) COMMENT='用户-应用关联';

-- 4. 认证账户（统一存储多种认证方式）
CREATE TABLE IF NOT EXISTS user_auth_account (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT NOT NULL COMMENT '用户ID，关联 user.id',
  identity_type VARCHAR(32) NOT NULL COMMENT 'password / oidc / wechat_mp / wechat_oa / sms',
  identifier    VARCHAR(255) NOT NULL COMMENT '该认证方式下的登录标识：password=用户名，oidc=外部sub，wechat=openid，sms=手机号',
  credential    VARCHAR(512) COMMENT '密码 bcrypt hash / OIDC refresh_token(加密)',
  issuer        VARCHAR(255) COMMENT 'OIDC iss；微信 appid',
  expires_at    DATETIME COMMENT 'token 过期时间（仅第三方登录使用）',
  refresh_token TEXT COMMENT 'OIDC refresh_token（加密存储，仅第三方登录使用）',
  union_id      VARCHAR(255) COMMENT '微信 unionid，跨应用打通（仅第三方登录使用）',
  verified      TINYINT DEFAULT 0 COMMENT '是否已验证',
  credential_updated_at DATETIME COMMENT '凭据最后更新时间（改密/换绑）',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  -- password 方式下 identifier 即用户名，天然保证用户名唯一
  UNIQUE KEY uk_type_identifier (identity_type, identifier),
  -- 第三方登录防重复绑定（issuer 非空的场景下生效）
  UNIQUE KEY uk_issuer_type_identifier (issuer, identity_type, identifier),
  INDEX idx_uaa_user (user_id),
  INDEX idx_union_id (union_id),
  FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) COMMENT='认证账户：一个用户可挂多种登录方式，登录查找入口=(identity_type, identifier)';

-- 5. 权限/资源（合并 RBAC Permission + Wolf Resource）
CREATE TABLE IF NOT EXISTS permission (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  app_id        BIGINT NOT NULL COMMENT '所属应用ID，关联 application.id',
  category      VARCHAR(64) COMMENT '分类标签，如"订单管理"',
  code          VARCHAR(128) NOT NULL COMMENT '权限编码，如 order:create',
  name          VARCHAR(64) COMMENT '权限名称，如"创建订单"',
  perm_type     VARCHAR(16) DEFAULT 'API' COMMENT 'MENU / API / BUTTON / DATA',
  -- 以下为原 resource 表字段，合并到 permission 中
  resource_path VARCHAR(255) COMMENT 'URL 模式，如 /api/order/:id；MENU 时为路由路径',
  http_method   VARCHAR(16) DEFAULT 'ALL' COMMENT 'ALL / GET / POST / PUT / DELETE / PATCH',
  match_type    VARCHAR(16) DEFAULT 'exact' COMMENT 'exact / prefix / suffix',
  parent_id     BIGINT DEFAULT 0 COMMENT 'MENU 树形结构用，0=顶级',
  icon          VARCHAR(64) COMMENT '菜单图标',
  sort          INT DEFAULT 0 COMMENT '排序',
  hidden        TINYINT DEFAULT 0 COMMENT '1=菜单隐藏',
  -- 通用字段
  description   VARCHAR(255) COMMENT '权限说明',
  status        TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  UNIQUE KEY uk_app_code (app_id, code),
  INDEX idx_perm_type (perm_type),
  INDEX idx_perm_status (status),
  INDEX idx_parent (parent_id),
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) COMMENT='权限原子项（合并了 Resource 的 URL 四元组）';

-- 6. 角色（RBAC 的 R）
CREATE TABLE IF NOT EXISTS `role` (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  app_id      BIGINT NOT NULL COMMENT '角色按应用隔离',
  code        VARCHAR(64) NOT NULL COMMENT '如 admin, auditor, finance_viewer',
  name        VARCHAR(64) NOT NULL COMMENT '角色名称',
  description VARCHAR(255) COMMENT '角色说明',
  status      TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  UNIQUE KEY uk_app_role_code (app_id, code),
  INDEX idx_role_status (status),
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) COMMENT='角色';

-- 7. 用户授角色（含应用上下文）
CREATE TABLE IF NOT EXISTS user_role (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id    BIGINT NOT NULL COMMENT '用户ID，关联 user.id',
  role_id    BIGINT NOT NULL COMMENT '角色ID，关联 role.id',
  app_id     BIGINT NOT NULL COMMENT '同一用户在不同应用可有不同角色',
  expires_at DATETIME COMMENT '临时授权到期时间',
  created_by BIGINT COMMENT '授权人 user_id',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  UNIQUE KEY uk_user_role_app (user_id, role_id, app_id),
  INDEX idx_role_user (role_id),
  INDEX idx_app (app_id),
  FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES `role`(id) ON DELETE CASCADE,
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) COMMENT='用户-角色-应用关联';

-- 8. 角色赋权
CREATE TABLE IF NOT EXISTS role_permission (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  role_id       BIGINT NOT NULL COMMENT '角色ID，关联 role.id',
  permission_id BIGINT NOT NULL COMMENT '权限ID，关联 permission.id',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  UNIQUE KEY uk_role_perm (role_id, permission_id),
  INDEX idx_perm (permission_id),
  FOREIGN KEY (role_id) REFERENCES `role`(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
) COMMENT='角色拥有哪些权限';

-- 9. 审计日志（所有经过网关的访问均记录）
CREATE TABLE IF NOT EXISTS audit_log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  app_id     BIGINT COMMENT '应用ID，NULL 表示未能解析出所属应用',
  user_id    BIGINT COMMENT '操作人用户ID，NULL 表示匿名访问',
  action     VARCHAR(64) COMMENT 'LOGIN / ACCESS_CHECK / PERMISSION_CHANGE / ROLE_ASSIGN',
  resource   VARCHAR(1024) COMMENT '请求的 URL 或操作的资源标识',
  method     VARCHAR(16) COMMENT 'HTTP method',
  status     TINYINT COMMENT '1=允许 0=拒绝',
  reason     VARCHAR(256) COMMENT '拒绝原因',
  client_ip  VARCHAR(45) COMMENT '客户端IP（兼容 IPv6，故为 45 位）',
  user_agent TEXT COMMENT 'User-Agent 原文，便于排查客户端类型',
  request_id VARCHAR(36) COMMENT '链路追踪 ID',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间（审计表只追加，不修改）',
  INDEX idx_audit_user (user_id),
  INDEX idx_app_time (app_id, created_at),
  INDEX idx_created_at (created_at),
  INDEX idx_request_id (request_id)
) COMMENT='审计日志（不可变，只追加不修改）';


-- ============================================================
-- 二、basic_* 新表结构
-- ============================================================

-- basic_app 应用（多应用隔离）
CREATE TABLE IF NOT EXISTS `basic_app` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_code`    VARCHAR(64) NOT NULL COMMENT '短标识，如 order、crm、admin',
    `name`        VARCHAR(64) NOT NULL COMMENT '应用名称',
    `base_path`   VARCHAR(128) DEFAULT NULL COMMENT '该应用 API 统一前缀，如 /api/order',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '应用描述',
    `status`      TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
    `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_basic_app_code` (`app_code`),
    KEY `idx_basic_app_status` (`status`)
) COMMENT='应用（多应用隔离）';


-- basic_group 用户岗位/职位/Title
CREATE TABLE IF NOT EXISTS `basic_group` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
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
    KEY `basic_group_org_id_IDX` (`org_id`)
) COMMENT='用户岗位/职位/Title';


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
) COMMENT='用户组与用户组关联表（组层级）';


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
) COMMENT='菜单与资源关联表';


-- basic_menu 菜单
-- 注：MySQL 原脚本的 BIGINT UNSIGNED 在 H2 中无对应类型，统一降级为 BIGINT。
CREATE TABLE IF NOT EXISTS `basic_menu` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `app_id`              BIGINT NOT NULL DEFAULT 0 COMMENT '应用ID，关联 basic_app.id',
    `parent_id`           BIGINT NOT NULL DEFAULT 0 COMMENT '父级ID',
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
    `update_user`         BIGINT NOT NULL DEFAULT 0 COMMENT '更新人id',
    `update_time`         DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '更新时间',
    `remark`              VARCHAR(200) NOT NULL DEFAULT '' COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_menu_app_id` (`app_id`),
    KEY `idx_menu_parent_id` (`parent_id`)
) COMMENT='菜单';


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
) COMMENT='操作日志记录';


-- basic_resource 权限原子项（合并了 Resource 的 URL 四元组）
CREATE TABLE IF NOT EXISTS `basic_resource` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_id`      BIGINT NOT NULL COMMENT '所属应用ID，关联 basic_app.id',
    `app_code`    VARCHAR(64) NOT NULL COMMENT '所属应用编码（冗余），权限校验按它收敛，见 basic.rbac.app-code',
    `name`        VARCHAR(64) DEFAULT NULL COMMENT '权限名称，如"创建订单"',
    `path`        VARCHAR(255) DEFAULT NULL COMMENT 'URL 模式，如 /api/order/:id；MENU 时为路由路径',
    `http_method` VARCHAR(16) DEFAULT 'ALL' COMMENT 'ALL / GET / POST / PUT / DELETE / PATCH',
    `match_type`  VARCHAR(16) DEFAULT 'exact' COMMENT 'exact / prefix / suffix',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '资源说明',
    `status`      TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
    `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_resource_app_code` (`app_code`)
) COMMENT='权限原子项（合并了 Resource 的 URL 四元组）';


-- basic_role 角色表
CREATE TABLE IF NOT EXISTS `basic_role` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `app_id`      BIGINT NOT NULL DEFAULT 0 COMMENT '应用ID，关联 basic_app.id',
    `code`        VARCHAR(50) NOT NULL DEFAULT '' COMMENT '角色编码',
    `name`        VARCHAR(50) NOT NULL DEFAULT '' COMMENT '角色名',
    `type`        TINYINT NOT NULL DEFAULT 0 COMMENT '角色类型：1-系统角色 2-自定义角色',
    `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 2-停用',
    `del_flag`    TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    `create_user` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人id',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` BIGINT NOT NULL DEFAULT 0 COMMENT '更新人id',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark`      VARCHAR(50) NOT NULL DEFAULT '' COMMENT '备注',
    `master`      INT NOT NULL DEFAULT 2 COMMENT '是否是管理员: 1-管理员；2-普通',
    PRIMARY KEY (`id`),
    KEY `idx_role_app_id` (`app_id`)
) COMMENT='角色表';


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
) COMMENT='用户组与角色关联表';


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
) COMMENT='角色权限关联表';


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
    `password`            VARCHAR(100) NOT NULL DEFAULT '' COMMENT '登录密码（BCrypt 哈希）',
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
) COMMENT='基础用户表（已整合旧 user 主表字段）';


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
) COMMENT='用户最后登录角色，解决多端登录';


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
) COMMENT='员工岗位关联表';


-- basic_user_role 基础用户关联角色表
CREATE TABLE IF NOT EXISTS `basic_user_role` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `app_id`      BIGINT NOT NULL DEFAULT 0 COMMENT '应用ID，关联 basic_app.id',
    `user_id`     BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
    `role_id`     BIGINT NOT NULL DEFAULT 0 COMMENT '角色ID',
    `del_flag`    TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    `create_user` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人id',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` BIGINT NOT NULL DEFAULT 0 COMMENT '更新人id',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark`      VARCHAR(200) NOT NULL DEFAULT '' COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `basic_user_role_user_id_IDX` (`user_id`),
    KEY `basic_user_role_role_id_IDX` (`role_id`)
) COMMENT='基础用户关联角色表';
