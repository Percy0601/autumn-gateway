-- ============================================================
-- 登录安全表：失败次数统计 + 账号锁定
-- 与 oauth2 三张表一样，建在 autumn-security 库
-- ============================================================

CREATE TABLE IF NOT EXISTS user_login_security (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  login_key       VARCHAR(128) NOT NULL COMMENT '登录标识：用户名（也可扩展为 用户名@IP）',
  failed_count    INT DEFAULT 0 COMMENT '连续失败次数，成功登录或解锁后清零',
  locked_until    DATETIME NULL COMMENT '锁定到期时间，NULL 表示未锁定',
  last_failed_at  DATETIME NULL COMMENT '最近一次失败时间',
  last_success_at DATETIME NULL COMMENT '最近一次成功时间',
  last_login_ip   VARCHAR(64) COMMENT '最近一次登录来源 IP',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_login_key (login_key),
  INDEX idx_locked_until (locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录失败计数与账号锁定';
