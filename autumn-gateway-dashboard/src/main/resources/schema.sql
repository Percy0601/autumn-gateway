-- ============================================================
-- Autumn Gateway Dashboard — 优化后的数据库表结构
-- 基于 Wolf RBAC 模型，修剪了 category / role_relation / role_constraint
-- ============================================================

-- ============================================================
-- 1. 应用（多应用隔离）
-- ============================================================
CREATE TABLE IF NOT EXISTS application (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  appid       VARCHAR(64) NOT NULL COMMENT '短标识，如 order、crm、admin',
  name        VARCHAR(64) NOT NULL,
  base_path   VARCHAR(128) COMMENT '该应用 API 统一前缀，如 /api/order',
  description VARCHAR(255),
  status      TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_appid (appid),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用（多应用隔离）';

-- ============================================================
-- 2. 用户（全局共享）
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64) UNIQUE COMMENT '内部登录名，第三方登录用户可空',
  nickname      VARCHAR(64),
  avatar        VARCHAR(255),
  email         VARCHAR(128),
  phone         VARCHAR(32),
  emp_no        VARCHAR(64) COMMENT '员工工号',
  status        TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
  last_login_at DATETIME,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_email (email),
  INDEX idx_phone (phone),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表（全局共享）';

-- ============================================================
-- 3. 用户在哪些应用下有身份（Wolf 核心设计）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_app (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    app_id     BIGINT NOT NULL,
    is_admin   TINYINT DEFAULT 0 COMMENT '是否该应用管理员',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_app (user_id, app_id),
    INDEX idx_app_user (app_id, user_id),
    FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-应用关联';

-- ============================================================
-- 4. 认证账户（统一存储多种认证方式）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_auth_account (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT NOT NULL,
  identity_type VARCHAR(32) NOT NULL COMMENT 'password / oidc / wechat_mp / wechat_oa / sms',
  identifier    VARCHAR(255) NOT NULL COMMENT '手机号 / openid / oidc_sub / 邮箱',
  credential    VARCHAR(512) COMMENT '密码 bcrypt hash / OIDC refresh_token(加密)',
  issuer        VARCHAR(255) COMMENT 'OIDC iss；微信 appid',
  expires_at    DATETIME COMMENT 'token 过期时间',
  refresh_token TEXT COMMENT 'OIDC refresh_token（加密存储）',
  union_id      VARCHAR(255) COMMENT '微信 unionid，跨应用打通',
  verified      TINYINT DEFAULT 0 COMMENT '是否已验证',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_type_identifier (identity_type, identifier),
  INDEX idx_user (user_id),
  INDEX idx_issuer (issuer),
  INDEX idx_union_id (union_id),
  FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认证账户：一个用户可挂多种登录方式';

-- ============================================================
-- 5. 权限/资源（合并 RBAC Permission + Wolf Resource）
--     一个权限定义 = 一个业务动作 + 它保护的 URL 模式
-- ============================================================
CREATE TABLE IF NOT EXISTS permission (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id        BIGINT NOT NULL,
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
  description   VARCHAR(255),
  status        TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_code (app_id, code),
  INDEX idx_perm_type (perm_type),
  INDEX idx_status (status),
  INDEX idx_parent (parent_id),
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限原子项（合并了 Resource 的 URL 四元组）';

-- ============================================================
-- 6. 角色（RBAC 的 R）
-- ============================================================
CREATE TABLE IF NOT EXISTS `role` (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id      BIGINT NOT NULL COMMENT '角色按应用隔离',
  code        VARCHAR(64) NOT NULL COMMENT '如 admin, auditor, finance_viewer',
  name        VARCHAR(64) NOT NULL,
  `level`     INT DEFAULT 0 COMMENT '数值越大权限越高，替代 role_relation 角色继承',
  description VARCHAR(255),
  status      TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_role_code (app_id, code),
  INDEX idx_status (status),
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

-- ============================================================
-- 7. 用户授角色（含应用上下文）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_role (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  role_id    BIGINT NOT NULL,
  app_id     BIGINT NOT NULL COMMENT '同一用户在不同应用可有不同角色',
  expires_at DATETIME COMMENT '临时授权到期时间',
  created_by BIGINT COMMENT '授权人 user_id',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_role_app (user_id, role_id, app_id),
  INDEX idx_role_user (role_id),
  INDEX idx_app (app_id),
  FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES `role`(id) ON DELETE CASCADE,
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色-应用关联';

-- ============================================================
-- 8. 角色赋权
-- ============================================================
CREATE TABLE IF NOT EXISTS role_permission (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_perm (role_id, permission_id),
  INDEX idx_perm (permission_id),
  FOREIGN KEY (role_id) REFERENCES `role`(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色拥有哪些权限';

-- ============================================================
-- 9. 审计日志（所有经过网关的访问均记录）
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id     BIGINT,
  user_id    BIGINT,
  action     VARCHAR(64) COMMENT 'LOGIN / ACCESS_CHECK / PERMISSION_CHANGE / ROLE_ASSIGN',
  resource   VARCHAR(1024) COMMENT '请求的 URL 或操作的资源标识',
  method     VARCHAR(16) COMMENT 'HTTP method',
  status     TINYINT COMMENT '1=允许 0=拒绝',
  reason     VARCHAR(256) COMMENT '拒绝原因',
  client_ip  VARCHAR(45),
  user_agent TEXT,
  request_id VARCHAR(36) COMMENT '链路追踪 ID',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user (user_id),
  INDEX idx_app_time (app_id, created_at),
  INDEX idx_created_at (created_at),
  INDEX idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志（不可变，只追加不修改）';
