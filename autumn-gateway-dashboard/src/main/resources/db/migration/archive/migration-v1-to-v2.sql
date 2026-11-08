-- ============================================================
-- 存量库升级脚本：v1 → v2（账号体系统一）
-- 适用：autumn-security 库已用旧 schema.sql 建表的情况
-- 执行：mysql -u<user> -p autumn-security < migration-v1-to-v2.sql
-- 说明：每一步都做了存在性判断，可重复执行
-- ============================================================

SET @db := DATABASE();

-- ------------------------------------------------------------
-- 1. user 表新增 uuid（先可空 → 回填 → 收紧为 NOT NULL + 唯一键）
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user' AND COLUMN_NAME = 'uuid');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `user` ADD COLUMN uuid VARCHAR(36) NULL COMMENT ''对外统一标识（OIDC 的 sub）'' AFTER id',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填历史数据
UPDATE `user` SET uuid = UUID() WHERE uuid IS NULL OR uuid = '';

SET @ddl := 'ALTER TABLE `user` MODIFY uuid VARCHAR(36) NOT NULL COMMENT ''对外统一标识（OIDC 的 sub），生成后永不变化''';
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_uuid');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `user` ADD UNIQUE KEY uk_uuid (uuid)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. user 表新增 password_updated_at
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user' AND COLUMN_NAME = 'password_updated_at');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `user` ADD COLUMN password_updated_at DATETIME NULL COMMENT ''密码最后修改时间'' AFTER status',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. user_auth_account 表新增 credential_updated_at
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user_auth_account' AND COLUMN_NAME = 'credential_updated_at');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE user_auth_account ADD COLUMN credential_updated_at DATETIME NULL COMMENT ''凭据最后更新时间'' AFTER verified',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4. 回填 password 认证账户的 identifier（关键！否则存量账号无法登录）
--    新代码统一按 (identity_type, identifier) 查找，identifier 必须等于用户名
-- ------------------------------------------------------------
UPDATE user_auth_account a
JOIN `user` u ON u.id = a.user_id
SET a.identifier = u.username
WHERE a.identity_type = 'password'
  AND (a.identifier IS NULL OR a.identifier = '' OR a.identifier <> u.username);

-- 兜底：password 账户没有对应的 user（脏数据）时删除，避免 NOT NULL 收紧失败
DELETE a FROM user_auth_account a
LEFT JOIN `user` u ON u.id = a.user_id
WHERE a.identity_type = 'password' AND u.id IS NULL;

-- 若库里存在"用户没有 password 认证账户"的情况，按用户名补一条（初始密码需重置）
INSERT INTO user_auth_account (user_id, identity_type, identifier, credential, verified, created_at, updated_at)
SELECT u.id, 'password', u.username, NULL, 0, NOW(), NOW()
FROM `user` u
WHERE u.username IS NOT NULL AND u.username <> ''
  AND NOT EXISTS (
      SELECT 1 FROM user_auth_account a
      WHERE a.user_id = u.id AND a.identity_type = 'password'
  );

-- 收紧 identifier 为 NOT NULL（上面已保证无 NULL）
SET @ddl := 'ALTER TABLE user_auth_account MODIFY identifier VARCHAR(255) NOT NULL COMMENT ''该认证方式下的登录标识''';
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5. 新增第三方防重复绑定唯一键
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user_auth_account'
                  AND INDEX_NAME = 'uk_issuer_type_identifier');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE user_auth_account ADD UNIQUE KEY uk_issuer_type_identifier (issuer, identity_type, identifier)',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 6. 校验（应全部返回 0 行）
-- ------------------------------------------------------------
-- SELECT 'user.uuid 为空' AS problem, id FROM `user` WHERE uuid IS NULL OR uuid = '';
-- SELECT 'password 账户 identifier 与用户名不一致' AS problem, a.id
--   FROM user_auth_account a JOIN `user` u ON u.id = a.user_id
--  WHERE a.identity_type = 'password' AND a.identifier <> u.username;
-- SELECT 'credential 为空（需重置密码）' AS problem, a.id
--   FROM user_auth_account a WHERE a.identity_type = 'password' AND (a.credential IS NULL OR a.credential = '');
