-- ================================================================
-- 存量 MySQL 库「只补注释」同步脚本（不是建表脚本，勿加入 spring.sql.init.schema-locations）
--
-- 背景：schema-*.sql 里的 COMMENT 只在 CREATE TABLE 时生效。
--       线上库的表已存在，CREATE TABLE IF NOT EXISTS 不会刷新注释，
--       必须用 ALTER TABLE ... MODIFY COLUMN 显式带上 COMMENT。
--
-- 用法（在能连到目标库的机器上）：
--   mysql -h192.168.0.102 -uadmin -p autumn-security < db/sync-column-comments-mysql.sql
--
-- ⚠️ 执行前必读：
--   1. MODIFY COLUMN 需要完整列定义，本脚本的类型/默认值/NOT NULL 全部取自
--      schema-*.sql。若线上列定义与脚本不一致，执行后会被「改回脚本定义」——
--      请先 `SHOW CREATE TABLE <表名>` 与脚本比对，确认没有计划外的类型变更。
--   2. 仅含注释，不增删列、不改索引；语句幂等，可重复执行。
--   3. MySQL 8.0.29+ 对「仅注释变化」的 MODIFY 有机会走 INSTANT，但大表仍建议
--      在低峰期执行，并先用单表试跑（脚本按表分组，可直接截取某一段）。
--   4. 生成时间：2026-10-22，来源：schema-basic-mysql.sql / schema-oauth2.sql /
--      schema-login-security.sql。
-- ================================================================


-- ----------------------------------------------------------------
-- basic_* 基础表（dashboard）（schema-basic-mysql.sql）
-- ----------------------------------------------------------------

ALTER TABLE `basic_namespace` COMMENT='命名空间（租户）';
ALTER TABLE `basic_namespace` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键';
ALTER TABLE `basic_namespace` MODIFY COLUMN `code` VARCHAR(64) NOT NULL COMMENT '命名空间编码，如 default';
ALTER TABLE `basic_namespace` MODIFY COLUMN `name` VARCHAR(64) NOT NULL COMMENT '命名空间名称';
ALTER TABLE `basic_namespace` MODIFY COLUMN `description` VARCHAR(255) DEFAULT NULL COMMENT '命名空间描述';
ALTER TABLE `basic_namespace` MODIFY COLUMN `status` TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用';
ALTER TABLE `basic_namespace` MODIFY COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_namespace` MODIFY COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间';

ALTER TABLE `basic_app` COMMENT='应用（多应用隔离）';
ALTER TABLE `basic_app` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键';
ALTER TABLE `basic_app` MODIFY COLUMN `app_code` VARCHAR(64) NOT NULL COMMENT '短标识，如 order、crm、admin';
ALTER TABLE `basic_app` MODIFY COLUMN `name` VARCHAR(64) NOT NULL COMMENT '应用名称';
ALTER TABLE `basic_app` MODIFY COLUMN `base_path` VARCHAR(128) DEFAULT NULL COMMENT '该应用 API 统一前缀，如 /api/order';
ALTER TABLE `basic_app` MODIFY COLUMN `description` VARCHAR(255) DEFAULT NULL COMMENT '应用描述';
ALTER TABLE `basic_app` MODIFY COLUMN `status` TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用';
ALTER TABLE `basic_app` MODIFY COLUMN `namespace_id` BIGINT DEFAULT NULL COMMENT '所属命名空间ID，关联 basic_namespace.id';
ALTER TABLE `basic_app` MODIFY COLUMN `namespace_code` VARCHAR(64) DEFAULT NULL COMMENT '所属命名空间编码';
ALTER TABLE `basic_app` MODIFY COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_app` MODIFY COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间';

ALTER TABLE `basic_group` COMMENT='用户岗位/职位/Title';
ALTER TABLE `basic_group` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_group` MODIFY COLUMN `namespace_id` BIGINT DEFAULT NULL COMMENT '所属命名空间ID，关联 basic_namespace.id';
ALTER TABLE `basic_group` MODIFY COLUMN `namespace_code` VARCHAR(64) DEFAULT NULL COMMENT '所属命名空间编码（冗余存储）';
ALTER TABLE `basic_group` MODIFY COLUMN `name` VARCHAR(100) NOT NULL COMMENT '岗位名称，职位名称，title';
ALTER TABLE `basic_group` MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_group` MODIFY COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `basic_group` MODIFY COLUMN `deleted` INT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是';
ALTER TABLE `basic_group` MODIFY COLUMN `code` VARCHAR(100) NOT NULL DEFAULT ' COMMENT '岗位编码';
ALTER TABLE `basic_group` MODIFY COLUMN `org_id` BIGINT NOT NULL DEFAULT 0 COMMENT '部门名称';
ALTER TABLE `basic_group` MODIFY COLUMN `job_id` BIGINT NOT NULL DEFAULT 0 COMMENT '岗位ID';
ALTER TABLE `basic_group` MODIFY COLUMN `status` INT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-停用';

ALTER TABLE `basic_group_group` COMMENT='用户组与用户组关联表（组层级）';
ALTER TABLE `basic_group_group` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_group_group` MODIFY COLUMN `parent_group_id` BIGINT NOT NULL DEFAULT 0 COMMENT '上级用户组ID';
ALTER TABLE `basic_group_group` MODIFY COLUMN `group_id` BIGINT NOT NULL DEFAULT 0 COMMENT '下级用户组ID';
ALTER TABLE `basic_group_group` MODIFY COLUMN `deleted` INT NOT NULL DEFAULT 0 COMMENT '删除状态：0-否 1-是';
ALTER TABLE `basic_group_group` MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_group_group` MODIFY COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `basic_group_group` MODIFY COLUMN `op_user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '操作人';

ALTER TABLE `basic_menu_resource` COMMENT='菜单与资源关联表';
ALTER TABLE `basic_menu_resource` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_menu_resource` MODIFY COLUMN `menu_id` BIGINT NOT NULL DEFAULT 0 COMMENT '菜单ID';
ALTER TABLE `basic_menu_resource` MODIFY COLUMN `resource_id` BIGINT NOT NULL DEFAULT 0 COMMENT '资源ID';
ALTER TABLE `basic_menu_resource` MODIFY COLUMN `deleted` INT NOT NULL DEFAULT 0 COMMENT '删除状态：0-否 1-是';
ALTER TABLE `basic_menu_resource` MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_menu_resource` MODIFY COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `basic_menu_resource` MODIFY COLUMN `op_user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '操作人';

ALTER TABLE `basic_menu` COMMENT='菜单';
ALTER TABLE `basic_menu` MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_menu` MODIFY COLUMN `app_id` BIGINT NOT NULL DEFAULT 0 COMMENT '应用ID，关联 basic_app.id';
ALTER TABLE `basic_menu` MODIFY COLUMN `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父级ID';
ALTER TABLE `basic_menu` MODIFY COLUMN `menu_type` TINYINT NOT NULL DEFAULT 0 COMMENT '权限类型：1-目录 2-菜单 3-按钮';
ALTER TABLE `basic_menu` MODIFY COLUMN `menu_name` VARCHAR(50) NOT NULL DEFAULT ' COMMENT '菜单名称';
ALTER TABLE `basic_menu` MODIFY COLUMN `menu_url` VARCHAR(2000) NOT NULL DEFAULT ' COMMENT '接口url（多个采用英文逗号分隔）';
ALTER TABLE `basic_menu` MODIFY COLUMN `menu_identification` VARCHAR(200) NOT NULL DEFAULT ' COMMENT '菜单或按钮标识';
ALTER TABLE `basic_menu` MODIFY COLUMN `menu_icon` VARCHAR(100) NOT NULL DEFAULT ' COMMENT '菜单图标';
ALTER TABLE `basic_menu` MODIFY COLUMN `sort_num` INT NOT NULL DEFAULT 0 COMMENT '菜单排序';
ALTER TABLE `basic_menu` MODIFY COLUMN `menu_status` TINYINT NOT NULL DEFAULT 1 COMMENT '菜单状态：1-启用 2-停用';
ALTER TABLE `basic_menu` MODIFY COLUMN `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是';
ALTER TABLE `basic_menu` MODIFY COLUMN `create_user` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人id';
ALTER TABLE `basic_menu` MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '创建时间';
ALTER TABLE `basic_menu` MODIFY COLUMN `update_user` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '更新人id';
ALTER TABLE `basic_menu` MODIFY COLUMN `update_time` DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '更新时间';
ALTER TABLE `basic_menu` MODIFY COLUMN `remark` VARCHAR(200) NOT NULL DEFAULT ' COMMENT '备注';

ALTER TABLE `basic_oper_log` COMMENT='操作日志记录';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `app_code` VARCHAR(50) NOT NULL DEFAULT ' COMMENT '系统标识';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `business_type` VARCHAR(50) NOT NULL DEFAULT ' COMMENT '业务类型';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `title` VARCHAR(100) NOT NULL DEFAULT ' COMMENT '请求标题';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `request_id` VARCHAR(50) NOT NULL DEFAULT ' COMMENT '请求ID';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `request_url` VARCHAR(200) NOT NULL DEFAULT ' COMMENT '请求URL';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `request_method` VARCHAR(100) NOT NULL DEFAULT ' COMMENT '请求方法';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `request_data` VARCHAR(2000) NOT NULL DEFAULT ' COMMENT '请求数据';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `class_method` VARCHAR(200) NOT NULL DEFAULT ' COMMENT '执行方法';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `consume_time` INT NOT NULL DEFAULT 0 COMMENT '执行时间（毫秒）';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `response_data` VARCHAR(2000) NOT NULL DEFAULT ' COMMENT '响应结果';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '操作状态：0-正常 1-异常';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `error_msg` VARCHAR(2000) NOT NULL DEFAULT ' COMMENT '操作错误消息';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `oper_id` BIGINT NOT NULL DEFAULT 0 COMMENT '操作人';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `oper_ip` VARCHAR(50) NOT NULL DEFAULT ' COMMENT '操作人IP';
ALTER TABLE `basic_oper_log` MODIFY COLUMN `op_time` DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '操作时间';

ALTER TABLE `basic_resource` COMMENT='权限原子项（合并了 Resource 的 URL 四元组）';
ALTER TABLE `basic_resource` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键';
ALTER TABLE `basic_resource` MODIFY COLUMN `app_id` BIGINT NOT NULL COMMENT '所属应用ID，关联 basic_app.id';
ALTER TABLE `basic_resource` MODIFY COLUMN `app_code` VARCHAR(64) NOT NULL COMMENT '所属应用编码（冗余），权限校验按它收敛，见 basic.rbac.app-code';
ALTER TABLE `basic_resource` MODIFY COLUMN `name` VARCHAR(64) DEFAULT NULL COMMENT '权限名称，如"创建订单"';
ALTER TABLE `basic_resource` MODIFY COLUMN `path` VARCHAR(255) DEFAULT NULL COMMENT 'URL 模式，如 /api/order/:id；MENU 时为路由路径';
ALTER TABLE `basic_resource` MODIFY COLUMN `http_method` VARCHAR(64) DEFAULT 'ALL' COMMENT 'HTTP 方法，支持多选逗号分隔，如 GET,POST；ALL 表示全部';
ALTER TABLE `basic_resource` MODIFY COLUMN `match_type` VARCHAR(16) DEFAULT 'exact' COMMENT 'exact / prefix / suffix';
ALTER TABLE `basic_resource` MODIFY COLUMN `description` VARCHAR(255) DEFAULT NULL COMMENT '资源说明';
ALTER TABLE `basic_resource` MODIFY COLUMN `status` TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用';
ALTER TABLE `basic_resource` MODIFY COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_resource` MODIFY COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间';

ALTER TABLE `basic_role` COMMENT='角色表';
ALTER TABLE `basic_role` MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_role` MODIFY COLUMN `namespace_id` BIGINT NOT NULL DEFAULT 0 COMMENT '命名空间ID，关联 basic_namespace.id';
ALTER TABLE `basic_role` MODIFY COLUMN `namespace_code` VARCHAR(64) NOT NULL DEFAULT ' COMMENT '命名空间编码';
ALTER TABLE `basic_role` MODIFY COLUMN `code` VARCHAR(50) NOT NULL DEFAULT ' COMMENT '角色编码';
ALTER TABLE `basic_role` MODIFY COLUMN `name` VARCHAR(50) NOT NULL DEFAULT ' COMMENT '角色名';
ALTER TABLE `basic_role` MODIFY COLUMN `type` TINYINT NOT NULL DEFAULT 0 COMMENT '角色类型：1-系统角色 2-自定义角色';
ALTER TABLE `basic_role` MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 2-停用';
ALTER TABLE `basic_role` MODIFY COLUMN `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是';
ALTER TABLE `basic_role` MODIFY COLUMN `create_user` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人id';
ALTER TABLE `basic_role` MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_role` MODIFY COLUMN `update_user` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '更新人id';
ALTER TABLE `basic_role` MODIFY COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `basic_role` MODIFY COLUMN `remark` VARCHAR(50) NOT NULL DEFAULT ' COMMENT '备注';
ALTER TABLE `basic_role` MODIFY COLUMN `master` INT NOT NULL DEFAULT 2 COMMENT '是否是管理员: 1-管理员；2-普通';

ALTER TABLE `basic_role_group` COMMENT='用户组与角色关联表';
ALTER TABLE `basic_role_group` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_role_group` MODIFY COLUMN `role_id` BIGINT NOT NULL DEFAULT 0 COMMENT '角色ID';
ALTER TABLE `basic_role_group` MODIFY COLUMN `group_id` BIGINT NOT NULL DEFAULT 0 COMMENT '用户组ID';
ALTER TABLE `basic_role_group` MODIFY COLUMN `deleted` INT NOT NULL DEFAULT 0 COMMENT '删除状态：0-否 1-是';
ALTER TABLE `basic_role_group` MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_role_group` MODIFY COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `basic_role_group` MODIFY COLUMN `op_user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '操作人';

ALTER TABLE `basic_role_permission` COMMENT='角色权限关联表';
ALTER TABLE `basic_role_permission` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_role_permission` MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '角色Id';
ALTER TABLE `basic_role_permission` MODIFY COLUMN `ref_id` BIGINT NOT NULL COMMENT 'data_scope_id/menu_id';
ALTER TABLE `basic_role_permission` MODIFY COLUMN `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_role_permission` MODIFY COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `basic_role_permission` MODIFY COLUMN `del_flag` INT NOT NULL DEFAULT 0 COMMENT '删除状态: 0:否，1：是';
ALTER TABLE `basic_role_permission` MODIFY COLUMN `op_user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '操作人Id';
ALTER TABLE `basic_role_permission` MODIFY COLUMN `type` INT NOT NULL DEFAULT 1 COMMENT '权限类型：1-菜单权限；2-资源；3-数据权限';

ALTER TABLE `basic_user` COMMENT='基础用户表（已整合旧 user 主表字段）';
ALTER TABLE `basic_user` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_user` MODIFY COLUMN `uuid` VARCHAR(36) DEFAULT NULL COMMENT '对外统一标识（OIDC 的 sub）：生成后永不变化，避免暴露自增ID、便于跨库合并；唯一，可为 NULL';
ALTER TABLE `basic_user` MODIFY COLUMN `username` VARCHAR(64) DEFAULT NULL COMMENT '登录名（工号语义）：创建后禁止修改，如需变更只能后台改库；唯一，可为 NULL';
ALTER TABLE `basic_user` MODIFY COLUMN `nickname` VARCHAR(64) NOT NULL DEFAULT ' COMMENT '昵称/姓名';
ALTER TABLE `basic_user` MODIFY COLUMN `avatar` VARCHAR(255) NOT NULL DEFAULT ' COMMENT '头像地址';
ALTER TABLE `basic_user` MODIFY COLUMN `email` VARCHAR(128) NOT NULL DEFAULT ' COMMENT '邮箱';
ALTER TABLE `basic_user` MODIFY COLUMN `phone` VARCHAR(32) NOT NULL DEFAULT ' COMMENT '手机号';
ALTER TABLE `basic_user` MODIFY COLUMN `emp_no` VARCHAR(50) NOT NULL DEFAULT ' COMMENT 'ESB人员工号';
ALTER TABLE `basic_user` MODIFY COLUMN `admin` INT NOT NULL DEFAULT 0 COMMENT '是否是管理员：1-是；0：否';
ALTER TABLE `basic_user` MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0=禁用';
ALTER TABLE `basic_user` MODIFY COLUMN `password` VARCHAR(100) NOT NULL DEFAULT ' COMMENT '登录密码（BCrypt 哈希），参考 sys_user 方式';
ALTER TABLE `basic_user` MODIFY COLUMN `login_error_count` INT NOT NULL DEFAULT 5 COMMENT '剩余登录重试次数：每次密码错误 -1，减到 0 自动禁用账号；登录成功 / 重置密码 / 管理员启用时恢复为 5';
ALTER TABLE `basic_user` MODIFY COLUMN `latest_role_id` BIGINT NOT NULL DEFAULT 0 COMMENT '最近登录使用角色Id';
ALTER TABLE `basic_user` MODIFY COLUMN `password_updated_at` DATETIME DEFAULT NULL COMMENT '密码最后修改时间，用于密码有效期/强制改密';
ALTER TABLE `basic_user` MODIFY COLUMN `last_login_at` DATETIME DEFAULT NULL COMMENT '最近登录时间';
ALTER TABLE `basic_user` MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_user` MODIFY COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `basic_user_access_role` COMMENT='用户最后登录角色，解决多端登录';
ALTER TABLE `basic_user_access_role` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_user_access_role` MODIFY COLUMN `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '用户Id';
ALTER TABLE `basic_user_access_role` MODIFY COLUMN `latest_role_id` BIGINT NOT NULL DEFAULT 0 COMMENT '最近登录使用角色Id';
ALTER TABLE `basic_user_access_role` MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_user_access_role` MODIFY COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `basic_user_access_role` MODIFY COLUMN `emp_id` VARCHAR(50) NOT NULL DEFAULT ' COMMENT 'jwt头部32位，用于索引。';
ALTER TABLE `basic_user_access_role` MODIFY COLUMN `del_flag` INT NOT NULL DEFAULT 0 COMMENT '删除标志：0-否，1-是';
ALTER TABLE `basic_user_access_role` MODIFY COLUMN `access_time` BIGINT NOT NULL DEFAULT 0 COMMENT '登录系统时间，就是生成jwt的create_time';

ALTER TABLE `basic_user_group` COMMENT='员工岗位关联表';
ALTER TABLE `basic_user_group` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_user_group` MODIFY COLUMN `deleted` INT NOT NULL DEFAULT 0 COMMENT '删除：0-否 1-是';
ALTER TABLE `basic_user_group` MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_user_group` MODIFY COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `basic_user_group` MODIFY COLUMN `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '用户Id';
ALTER TABLE `basic_user_group` MODIFY COLUMN `group_id` BIGINT NOT NULL DEFAULT 0 COMMENT '岗位Id';

ALTER TABLE `basic_user_role` COMMENT='基础用户关联角色表';
ALTER TABLE `basic_user_role` MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID';
ALTER TABLE `basic_user_role` MODIFY COLUMN `app_id` BIGINT NOT NULL DEFAULT 0 COMMENT '应用ID，关联 basic_app.id';
ALTER TABLE `basic_user_role` MODIFY COLUMN `user_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户ID';
ALTER TABLE `basic_user_role` MODIFY COLUMN `role_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '角色ID';
ALTER TABLE `basic_user_role` MODIFY COLUMN `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否 1-是';
ALTER TABLE `basic_user_role` MODIFY COLUMN `create_user` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人id';
ALTER TABLE `basic_user_role` MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `basic_user_role` MODIFY COLUMN `update_user` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '更新人id';
ALTER TABLE `basic_user_role` MODIFY COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `basic_user_role` MODIFY COLUMN `remark` VARCHAR(200) NOT NULL DEFAULT ' COMMENT '备注';

-- ----------------------------------------------------------------
-- OAuth2 四张表（authorization）（schema-oauth2.sql）
-- ----------------------------------------------------------------

ALTER TABLE `oauth2_registered_client` COMMENT='OAuth2 客户端注册信息（一条 = 一个接入方）';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `id` VARCHAR(100) NOT NULL COMMENT '主键：RegisteredClient 内部唯一 ID（UUID 字符串），仅供外键引用，不对外暴露';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `client_id` VARCHAR(100) NOT NULL COMMENT '客户端标识（对外唯一）：即 OAuth2 协议里的 client_id，如 web-client';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `client_id_issued_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '客户端注册/签发时间';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `client_secret` VARCHAR(200) DEFAULT NULL COMMENT '客户端密钥：PasswordEncoder 加密后的哈希（BCrypt 60 字符起步，故留 200）；NULL 表示公共客户端（无密钥）';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `client_secret_expires_at` TIMESTAMP DEFAULT NULL COMMENT '密钥过期时间；NULL = 永不过期（当前所有客户端均为 NULL）';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `client_name` VARCHAR(200) NOT NULL COMMENT '客户端显示名：授权确认页展示给用户看的名称';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `client_authentication_methods` VARCHAR(1000) NOT NULL COMMENT '客户端认证方式，逗号分隔：client_secret_basic / client_secret_post / none（公共客户端）';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `authorization_grant_types` VARCHAR(1000) NOT NULL COMMENT '允许的授权模式，逗号分隔：authorization_code / refresh_token / client_credentials / urn:ietf:params:oauth:grant-type:device_code';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `redirect_uris` VARCHAR(1000) DEFAULT NULL COMMENT '回调地址白名单，逗号分隔；必须精确匹配，否则授权请求被拒（invalid_redirect_uri）';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `post_logout_redirect_uris` VARCHAR(1000) DEFAULT NULL COMMENT 'OIDC 登出后可跳转的地址，逗号分隔；未登记则 /connect/logout 不能带 post_logout_redirect_uri';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `scopes` VARCHAR(1000) NOT NULL COMMENT '允许申请的 scope，逗号分隔：openid / profile / business:read 等';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `client_settings` VARCHAR(2000) NOT NULL COMMENT 'ClientSettings 的 JSON：requireProofKey（强制 PKCE）、requireAuthorizationConsent（是否弹授权确认页）等';
ALTER TABLE `oauth2_registered_client` MODIFY COLUMN `token_settings` VARCHAR(2000) NOT NULL COMMENT 'TokenSettings 的 JSON：各类令牌 TTL、reuseRefreshTokens、idTokenSignatureAlgorithm 等';

ALTER TABLE `oauth2_authorization` COMMENT='授权记录（授权码/访问令牌/刷新令牌/id_token 的完整生命周期）';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `id` VARCHAR(100) NOT NULL COMMENT '主键：本次授权记录 ID（UUID 字符串），与令牌无关';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `registered_client_id` VARCHAR(100) NOT NULL COMMENT '所属客户端：关联 oauth2_registered_client.id（注意是内部主键，不是 client_id）';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `principal_name` VARCHAR(200) NOT NULL COMMENT '资源所有者标识：即登录用户名（本实现取 basic_user.username），令牌 sub 在签发时被改写为 uuid';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `authorization_grant_type` VARCHAR(100) NOT NULL COMMENT '本次授权使用的模式：authorization_code / client_credentials / refresh_token 等';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `authorized_scopes` VARCHAR(1000) DEFAULT NULL COMMENT '用户最终授予的 scope，逗号分隔；未弹确认页时等于申请值';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `attributes` BLOB DEFAULT NULL COMMENT '授权会话上下文（JSON 字节流）：含 Principal、OAuth2AuthorizationRequest、当前认证信息等';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `state` VARCHAR(500) DEFAULT NULL COMMENT '授权请求的 state 原值：回调时用于防 CSRF，校验后不再使用';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `authorization_code_value` BLOB DEFAULT NULL COMMENT '授权码明文（字节流）；一次性使用，换令牌后写入 metadata 标记已失效';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `authorization_code_issued_at` TIMESTAMP DEFAULT NULL COMMENT '授权码签发时间';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `authorization_code_expires_at` TIMESTAMP DEFAULT NULL COMMENT '授权码过期时间（默认 5 分钟，OAuth2AuthorizationCodeRequestAuthenticationProvider 可配）';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `authorization_code_metadata` BLOB DEFAULT NULL COMMENT '授权码元数据（JSON 字节流）：invalidated、claims、issuedAt/expiresAt 冗余值';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `access_token_value` BLOB DEFAULT NULL COMMENT '访问令牌明文（JWT 字节流）；资源服务器验签用的就是它';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `access_token_issued_at` TIMESTAMP DEFAULT NULL COMMENT '访问令牌签发时间（JWT 的 iat）';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `access_token_expires_at` TIMESTAMP DEFAULT NULL COMMENT '访问令牌过期时间（JWT 的 exp）；默认 2 小时，见 DemoRegisteredClientInitializer';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `access_token_metadata` BLOB DEFAULT NULL COMMENT '访问令牌元数据（JSON 字节流）：claims、token_type、scopes、invalidated 等';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `access_token_type` VARCHAR(100) DEFAULT NULL COMMENT '令牌类型：Bearer（当前仅支持 Bearer）';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `access_token_scopes` VARCHAR(1000) DEFAULT NULL COMMENT '该访问令牌实际包含的 scope，逗号分隔（决定 userinfo 可返回哪些字段）';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `oidc_id_token_value` BLOB DEFAULT NULL COMMENT 'OIDC id_token（JWT 字节流）：仅 scope 含 openid 时才有值';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `oidc_id_token_issued_at` TIMESTAMP DEFAULT NULL COMMENT 'id_token 签发时间';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `oidc_id_token_expires_at` TIMESTAMP DEFAULT NULL COMMENT 'id_token 过期时间';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `oidc_id_token_metadata` BLOB DEFAULT NULL COMMENT 'id_token 元数据（JSON 字节流）：claims（含 sub/nickname/authorities）、nonce 等';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `refresh_token_value` BLOB DEFAULT NULL COMMENT '刷新令牌明文（字节流）：不透明字符串，非 JWT';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `refresh_token_issued_at` TIMESTAMP DEFAULT NULL COMMENT '刷新令牌签发时间';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `refresh_token_expires_at` TIMESTAMP DEFAULT NULL COMMENT '刷新令牌过期时间；默认 7 天，且 reuseRefreshTokens=false 时每次刷新都轮换';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `refresh_token_metadata` BLOB DEFAULT NULL COMMENT '刷新令牌元数据（JSON 字节流）：invalidated、claims 等';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `user_code_value` BLOB DEFAULT NULL COMMENT '设备码模式：用户码（如 WDJB-MJHT），供用户在验证页输入';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `user_code_issued_at` TIMESTAMP DEFAULT NULL COMMENT '用户码签发时间';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `user_code_expires_at` TIMESTAMP DEFAULT NULL COMMENT '用户码过期时间';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `user_code_metadata` BLOB DEFAULT NULL COMMENT '用户码元数据（JSON 字节流）';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `device_code_value` BLOB DEFAULT NULL COMMENT '设备码模式：设备码，设备端轮询令牌端点时携带';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `device_code_issued_at` TIMESTAMP DEFAULT NULL COMMENT '设备码签发时间';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `device_code_expires_at` TIMESTAMP DEFAULT NULL COMMENT '设备码过期时间';
ALTER TABLE `oauth2_authorization` MODIFY COLUMN `device_code_metadata` BLOB DEFAULT NULL COMMENT '设备码元数据（JSON 字节流）';

ALTER TABLE `oauth2_jwk` COMMENT='JWT 签名密钥对（含私钥，注意加密与授权）';
ALTER TABLE `oauth2_jwk` MODIFY COLUMN `id` VARCHAR(100) NOT NULL COMMENT '密钥ID（kid）：取值 autumn.authorization-server.jwk.key-id，多个实例共用同一条';
ALTER TABLE `oauth2_jwk` MODIFY COLUMN `jwk_set_json` TEXT NOT NULL COMMENT 'JWK Set JSON（含 RSA 私钥）：由 JwkSupport.serialize 写入，读取时校验私钥存在';
ALTER TABLE `oauth2_jwk` MODIFY COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次生成时间';
ALTER TABLE `oauth2_jwk` MODIFY COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间（密钥轮换时变更）';

ALTER TABLE `oauth2_authorization_consent` COMMENT='用户授权同意记录（同一用户+客户端仅一条）';
ALTER TABLE `oauth2_authorization_consent` MODIFY COLUMN `registered_client_id` VARCHAR(100) NOT NULL COMMENT '客户端：关联 oauth2_registered_client.id（联合主键之一）';
ALTER TABLE `oauth2_authorization_consent` MODIFY COLUMN `principal_name` VARCHAR(200) NOT NULL COMMENT '授权用户：登录用户名（联合主键之一）';
ALTER TABLE `oauth2_authorization_consent` MODIFY COLUMN `authorities` VARCHAR(1000) NOT NULL COMMENT '用户已同意的 scope 集合，逗号分隔；未列出的 scope 下次仍需确认';

-- ----------------------------------------------------------------
-- 登录安全表（authorization）（schema-login-security.sql）
-- ----------------------------------------------------------------

ALTER TABLE `user_login_security` COMMENT='登录失败计数与账号锁定（按登录标识维度，多实例共享）';
ALTER TABLE `user_login_security` MODIFY COLUMN `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键（仅内部使用，无业务含义）';
ALTER TABLE `user_login_security` MODIFY COLUMN `login_key` VARCHAR(128) NOT NULL COMMENT '登录标识：用户名（也可扩展为 用户名@IP）；唯一，锁定判定的查询键';
ALTER TABLE `user_login_security` MODIFY COLUMN `failed_count` INT DEFAULT 0 COMMENT '连续失败次数（成功登录或解锁后清零），达到 max-attempts 即触发锁定';
ALTER TABLE `user_login_security` MODIFY COLUMN `locked_until` DATETIME NULL COMMENT '锁定到期时间，NULL 表示未锁定；到期后无需人工干预即自动解锁';
ALTER TABLE `user_login_security` MODIFY COLUMN `last_failed_at` DATETIME NULL COMMENT '最近一次失败时间';
ALTER TABLE `user_login_security` MODIFY COLUMN `last_success_at` DATETIME NULL COMMENT '最近一次成功时间';
ALTER TABLE `user_login_security` MODIFY COLUMN `last_login_ip` VARCHAR(64) COMMENT '最近一次登录（成功或失败）来源 IP，便于审计';
ALTER TABLE `user_login_security` MODIFY COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次记录时间';
ALTER TABLE `user_login_security` MODIFY COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间';

