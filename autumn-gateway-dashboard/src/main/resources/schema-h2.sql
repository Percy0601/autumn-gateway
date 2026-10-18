-- ============================================================
-- Autumn Gateway Dashboard — 优化后的数据库表结构
-- 基于 Wolf RBAC 模型，修剪了 category / role_relation / role_constraint
-- 权限管理（permission / role_permission）已下线，统一走 basic_* 菜单/资源授权
-- ============================================================

-- ============================================================
-- 1. 应用（多应用隔离）
-- ============================================================
CREATE TABLE IF NOT EXISTS "application" (
  "ID"          BIGINT PRIMARY KEY AUTO_INCREMENT,
  "appid"       VARCHAR(64) NOT NULL,
  "NAME"        VARCHAR(64) NOT NULL,
  "base_path"   VARCHAR(128),
  "DESCRIPTION" VARCHAR(255),
  "STATUS"      TINYINT DEFAULT 1,
  "created_at"  DATETIME DEFAULT CURRENT_TIMESTAMP,
  "updated_at"  DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE ("appid"),
  INDEX ("STATUS")
);

-- ============================================================
-- 2. 用户（全局共享）
-- ============================================================
CREATE TABLE IF NOT EXISTS "user" (
  "ID"            BIGINT PRIMARY KEY AUTO_INCREMENT,
  "uuid"          VARCHAR(36) NOT NULL,
  "USERNAME"      VARCHAR(64) UNIQUE,
  "NICKNAME"      VARCHAR(64),
  "AVATAR"        VARCHAR(255),
  "EMAIL"         VARCHAR(128),
  "PHONE"         VARCHAR(32),
  "emp_no"        VARCHAR(64),
  "STATUS"        TINYINT DEFAULT 1,
  "password_updated_at" DATETIME,
  "last_login_at" DATETIME,
  "created_at"    DATETIME DEFAULT CURRENT_TIMESTAMP,
  "updated_at"    DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE ("uuid"),
  INDEX ("EMAIL"),
  INDEX ("PHONE"),
  INDEX ("STATUS")
);

-- ============================================================
-- 3. 用户在哪些应用下有身份（Wolf 核心设计）
-- ============================================================
CREATE TABLE IF NOT EXISTS "user_app" (
    "ID"         BIGINT AUTO_INCREMENT PRIMARY KEY,
    "user_id"    BIGINT NOT NULL,
    "app_id"     BIGINT NOT NULL,
    "is_admin"   BOOLEAN DEFAULT FALSE,
    "created_at" DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE ("user_id", "app_id"),
    INDEX ("app_id", "user_id")
);

-- ============================================================
-- 4. 认证账户（统一存储多种认证方式）
-- ============================================================
-- 注意：H2 对带引号标识符大小写敏感，而 Spring Data JDBC 默认给列名加引号，
-- 且未标注 @Column 的字段转大写、标注 @Column 的字段保持原样（小写）。
-- 因此此处表名与列名必须与实体映射的引号大小写完全一致，否则报 "Table/Column not found"。
CREATE TABLE IF NOT EXISTS "user_auth_account" (
  "ID"            BIGINT PRIMARY KEY AUTO_INCREMENT,
  "user_id"       BIGINT NOT NULL,
  "identity_type" VARCHAR(32) NOT NULL,
  "IDENTIFIER"    VARCHAR(255) NOT NULL,
  "CREDENTIAL"    VARCHAR(512),
  "ISSUER"        VARCHAR(255),
  "expires_at"    DATETIME,
  "refresh_token" TEXT,
  "union_id"      VARCHAR(255),
  "VERIFIED"      BOOLEAN DEFAULT FALSE,
  "credential_updated_at" DATETIME,
  "created_at"    DATETIME DEFAULT CURRENT_TIMESTAMP,
  "updated_at"    DATETIME DEFAULT CURRENT_TIMESTAMP,
  -- password 方式下 identifier 即用户名，天然保证用户名唯一
  UNIQUE ("identity_type", "IDENTIFIER"),
  -- 第三方登录防重复绑定（issuer 非空的场景下生效）
  UNIQUE ("ISSUER", "identity_type", "IDENTIFIER"),
  INDEX ("user_id"),
  INDEX ("union_id")
);

-- ============================================================
-- 5. 角色（RBAC 的 R）
-- ============================================================
CREATE TABLE IF NOT EXISTS "role" (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id      BIGINT NOT NULL,
  code        VARCHAR(64) NOT NULL,
  name        VARCHAR(64) NOT NULL,
  description VARCHAR(255),
  status      TINYINT DEFAULT 1,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ,
  UNIQUE KEY (app_id, code),
  INDEX (status)
);

-- ============================================================
-- 6. 用户授角色（含应用上下文）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_role (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  role_id    BIGINT NOT NULL,
  app_id     BIGINT NOT NULL,
  expires_at DATETIME,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY (user_id, role_id, app_id),
  INDEX (role_id),
  INDEX (app_id)
);

-- ============================================================
-- 7. 审计日志（所有经过网关的访问均记录）
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id     BIGINT,
  user_id    BIGINT,
  action     VARCHAR(64),
  resource   VARCHAR(1024),
  method     VARCHAR(16),
  status     TINYINT,
  reason     VARCHAR(256),
  client_ip  VARCHAR(45),
  user_agent TEXT,
  request_id VARCHAR(36),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX (user_id),
  INDEX (app_id, created_at),
  INDEX (created_at),
  INDEX (request_id)
);