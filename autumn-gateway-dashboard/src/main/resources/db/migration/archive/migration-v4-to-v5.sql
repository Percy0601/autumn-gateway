-- ============================================================
-- 存量库升级脚本：v4 → v5（basic_user 增加密码与连续登录失败次数）
-- 适用：autumn-security 库已用旧 schema 建表的情况
-- 执行：mysql -u<具备 ALTER 权限的账号> -p autumn-security < migration-v4-to-v5.sql
-- 说明：每一步都做了存在性判断，可重复执行。
--
-- 注意：
--   1. 本脚本只负责 DDL 结构变更（新增列）。
--   2. 列新增后，应用启动时的初始化器（NamespaceDataInitializer）会自动把
--      basic_user 中 admin 账号的 password 回填为 BCrypt(autumn.user.default-password)，
--      并把 login_error_count 兜底为 0（仅当为 NULL 时）。无需在本脚本里写数据。
--   3. 执行完本脚本后，请用具备 ALTER 权限的账号重启 autumn-gateway-dashboard 服务。
-- ============================================================

SET @db := DATABASE();

-- ------------------------------------------------------------
-- 1. basic_user 新增 password（BCrypt 哈希）
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_user' AND COLUMN_NAME = 'password');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `basic_user` ADD COLUMN password VARCHAR(100) NOT NULL DEFAULT '''' COMMENT ''登录密码（BCrypt 哈希），参考 sys_user 方式'' AFTER status',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. basic_user 新增 login_error_count（连续登录失败次数）
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_user' AND COLUMN_NAME = 'login_error_count');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `basic_user` ADD COLUMN login_error_count INT NOT NULL DEFAULT 0 COMMENT ''连续登录失败次数（成功登录清零，供锁定策略使用）'' AFTER password',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
