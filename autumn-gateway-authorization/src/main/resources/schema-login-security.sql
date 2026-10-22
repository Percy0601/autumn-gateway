-- ============================================================
-- 登录安全表：失败次数统计 + 账号锁定
--
-- 所属库：autumn-security（与 oauth2_* 四张表同库）
-- 加载方式：随应用启动由 spring.sql.init 执行，见
--           autumn-gateway-authorization/src/main/resources/application.properties
--           中 spring.sql.init.schema-locations=classpath:schema-oauth2.sql,classpath:schema-login-security.sql
--
-- 谁在读写：LoginAttemptService（登录失败计数、锁定判定、成功清零）
--           锁定时长/次数阈值来自 autumn.security.login.max-attempts / lock-minutes
--
-- 说明：与 dashboard 的 basic_user.login_error_count（剩余重试次数）是两套独立机制——
--       本表按「登录标识」维度计数，用于认证服务器侧的多实例共享锁定；
--       basic_user 那个字段服务于业务库侧账号状态。
-- ============================================================

CREATE TABLE IF NOT EXISTS user_login_security (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键（仅内部使用，无业务含义）',
  login_key       VARCHAR(128) NOT NULL COMMENT '登录标识：用户名（也可扩展为 用户名@IP）；唯一，锁定判定的查询键',
  failed_count    INT DEFAULT 0 COMMENT '连续失败次数（成功登录或解锁后清零），达到 max-attempts 即触发锁定',
  locked_until    DATETIME NULL COMMENT '锁定到期时间，NULL 表示未锁定；到期后无需人工干预即自动解锁',
  last_failed_at  DATETIME NULL COMMENT '最近一次失败时间',
  last_success_at DATETIME NULL COMMENT '最近一次成功时间',
  last_login_ip   VARCHAR(64) COMMENT '最近一次登录（成功或失败）来源 IP，便于审计',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次记录时间',
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  UNIQUE KEY uk_login_key (login_key),
  INDEX idx_locked_until (locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录失败计数与账号锁定（按登录标识维度，多实例共享）';
