CREATE DATABASE `autumn-security` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;


-- ============================================================
-- 1. 应用（多应用隔离）
-- ============================================================
CREATE TABLE application (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  appid       VARCHAR(64) NOT NULL COMMENT '短标识，如 order、crm、admin',
  name        VARCHAR(64) NOT NULL,
  base_path   VARCHAR(128) COMMENT '该应用 API 统一前缀，如 /api/order',
  description VARCHAR(255),
  status      TINYINT DEFAULT 1,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_appid (appid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用（Wolf 风格多应用隔离）';

-- ============================================================
-- 2. 用户（全局共享）
-- ============================================================
CREATE TABLE user (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64) UNIQUE COMMENT '内部登录名，第三方登录用户可空',
  nickname      VARCHAR(64),
  avatar        VARCHAR(255),
  email         VARCHAR(128),
  phone         VARCHAR(32),
  status        TINYINT DEFAULT 1 COMMENT '1=正常 0=禁用',
  last_login_at DATETIME,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表（全局共享）';

-- ============================================================
-- 3. 用户在哪些应用下有身份（Wolf 核心设计）
-- ============================================================
CREATE TABLE user_app (
  user_id     BIGINT NOT NULL,
  app_id      BIGINT NOT NULL,
  is_admin    TINYINT DEFAULT 0 COMMENT '1=该应用管理员（可登 console 管此应用）',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, app_id),
  INDEX idx_app (app_id),
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户与应用关联';

-- ============================================================
-- 4. 认证账户（统一存储多种认证方式）
-- ============================================================
CREATE TABLE user_auth_account (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT NOT NULL,
  identity_type VARCHAR(32) NOT NULL COMMENT 'password / oidc / wechat_mp / wechat_oa / sms',
  identifier    VARCHAR(255) NOT NULL COMMENT '手机号 / openid / oidc_sub / 邮箱',
  credential    VARCHAR(512) COMMENT '密码 bcrypt hash / OIDC refresh_token(加密)',
  issuer        VARCHAR(255) COMMENT 'OIDC iss (如 https://accounts.google.com)；微信 appid',
  expires_at    DATETIME COMMENT 'OIDC id_token 过期时间',
  refresh_token TEXT COMMENT 'OIDC refresh_token（加密存储）',
  union_id      VARCHAR(255) COMMENT '微信 unionid，跨应用打通',
  verified      TINYINT DEFAULT 0 COMMENT '是否已验证',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_type_identifier (identity_type, identifier),
  INDEX idx_user (user_id),
  INDEX idx_issuer (issuer),
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认证账户：一个用户可挂多种登录方式';

-- ============================================================
-- 5. 权限分类（Wolf 风格，用于管理后台分组）
-- ============================================================
CREATE TABLE category (
  id       BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id   BIGINT NOT NULL COMMENT '权限分类按应用隔离',
  name     VARCHAR(64) NOT NULL COMMENT '如：订单管理 / 用户管理 / 报表',
  sort     INT DEFAULT 0,
  UNIQUE KEY uk_app_category (app_id, name),
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限分类';

-- ============================================================
-- 6. 权限原子（RBAC 的 P）
-- ============================================================
CREATE TABLE permission (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id      BIGINT NOT NULL,
  category_id BIGINT COMMENT 'NULL=未分类',
  code        VARCHAR(128) NOT NULL COMMENT '如 order:create, dashboard:view',
  name        VARCHAR(64),
  perm_type   ENUM('MENU','API','BUTTON','DATA') DEFAULT 'API',
  description VARCHAR(255),
  status      TINYINT DEFAULT 1,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_code (app_id, code),
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE,
  FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限原子项';

-- ============================================================
-- 7. 角色（RBAC 的 R）
-- ============================================================
CREATE TABLE role (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id      BIGINT NOT NULL COMMENT '角色按应用隔离',
  code        VARCHAR(64) NOT NULL COMMENT '如 admin, auditor, finance_viewer',
  name        VARCHAR(64) NOT NULL,
  level       INT DEFAULT 0 COMMENT '用于继承排序（值越大权限越高）',
  description VARCHAR(255),
  status      TINYINT DEFAULT 1,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_code (app_id, code),
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

-- ============================================================
-- 8. 角色继承（RBAC1）
-- ============================================================
CREATE TABLE role_relation (
  parent_role_id BIGINT NOT NULL COMMENT '父角色（权限更多）',
  child_role_id  BIGINT NOT NULL COMMENT '子角色（继承父角色权限）',
  PRIMARY KEY (parent_role_id, child_role_id),
  FOREIGN KEY (parent_role_id) REFERENCES role(id) ON DELETE CASCADE,
  FOREIGN KEY (child_role_id) REFERENCES role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色继承关系（child 继承 parent 的所有权限）';

-- ============================================================
-- 9. 角色约束（RBAC2）
-- ============================================================
CREATE TABLE role_constraint (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  constraint_type ENUM('mutex','cardinality','prerequisite','runtime_mutex') NOT NULL,
  role_id         BIGINT NOT NULL COMMENT '约束主体角色',
  target_role_id  BIGINT COMMENT 'mutex/prerequisite 时指向另一个角色',
  max_users       INT COMMENT 'cardinality: 该角色最多可授予多少用户',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
  FOREIGN KEY (target_role_id) REFERENCES role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RBAC2 约束（互斥/基数/先决条件）';

-- ============================================================
-- 10. 用户授角色（含应用上下文）
-- ============================================================
CREATE TABLE user_role (
  user_id    BIGINT NOT NULL,
  role_id    BIGINT NOT NULL,
  app_id     BIGINT NOT NULL COMMENT '同一用户在不同应用可有不同角色',
  expires_at DATETIME COMMENT '临时授权到期时间',
  created_by BIGINT COMMENT '授权人 user_id',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_id, app_id),
  INDEX idx_role (role_id),
  INDEX idx_app (app_id),
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色-应用关联';

-- ============================================================
-- 11. 角色赋权
-- ============================================================
CREATE TABLE role_permission (
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色拥有哪些权限';

-- ============================================================
-- 12. 资源（Wolf 四元组：match_type + name + action + method）
-- ============================================================
CREATE TABLE resource (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id     BIGINT NOT NULL,
  parent_id  BIGINT DEFAULT 0 COMMENT 'MENU 树形结构用',
  res_type   ENUM('MENU','API','BUTTON','PAGE_ELEMENT') NOT NULL,
  match_type ENUM('exact','prefix','suffix') DEFAULT 'exact' COMMENT 'URL 匹配方式',
  name       VARCHAR(255) NOT NULL COMMENT 'MENU=路由路径; API=URL模式，如 /api/order/:id',
  action     VARCHAR(16) DEFAULT 'ALL' COMMENT 'HTTP method 或 ALL（Wolf 的 action）',
  icon       VARCHAR(64),
  sort       INT DEFAULT 0,
  hidden     TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_res (app_id, match_type, name, action),
  FOREIGN KEY (app_id) REFERENCES application(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源表（Wolf 四元组，网关层鉴权核心）';

-- ============================================================
-- 13. 权限关联资源
-- ============================================================
CREATE TABLE permission_resource (
  permission_id BIGINT NOT NULL,
  resource_id   BIGINT NOT NULL,
  PRIMARY KEY (permission_id, resource_id),
  FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE,
  FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限与资源的多对多关联';

-- ============================================================
-- 14. 审计日志（Wolf 风格）
-- ============================================================
CREATE TABLE audit_log (
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
  INDEX idx_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志（所有经过网关的访问均记录）';
