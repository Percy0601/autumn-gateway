-- ============================================================
-- 存量库升级脚本：v6 → v7（basic_user.login_error_count 语义反转）
-- 适用：autumn-security 库（MySQL）
-- 执行：mysql -uroot -p autumn-security < migration-v6-to-v7.sql
-- 说明：每一步都做了存在性判断，可重复执行。
--
-- 背景：login_error_count 原为「连续登录失败次数」（0 = 正常，达到
--       autumn.user.max-login-error-count 即禁用）。现统一改为「剩余登录重试次数」：
--         新建/改密/启用 → 5（满额）；密码错误 → -1；减到 0 → 自动禁用账号。
--       不改的话，存量账号 login_error_count 多为 0，反转后会立刻被判定为「次数耗尽」。
--
--   1. 列默认值 DEFAULT 0 → DEFAULT 5，并同步列注释；
--   2. 存量数据换算：启用中的账号按满额给，已停用的账号（含被自动锁定的）置 0，
--      后续由管理员在用户管理页「启用」时自动恢复满额；
--   3. 本脚本按 status 换算，结果幂等，可重复执行。
-- ============================================================

SET @db := DATABASE();

-- ------------------------------------------------------------
-- 1. 列定义：DEFAULT 0 -> DEFAULT 5 + 注释改写新语义
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_user' AND COLUMN_NAME = 'login_error_count');
SET @ddl := IF(@exists > 0,
    'ALTER TABLE `basic_user` MODIFY `login_error_count` INT NOT NULL DEFAULT 5 COMMENT ''剩余登录重试次数：每次密码错误 -1，减到 0 自动禁用账号；登录成功 / 重置密码 / 管理员启用时恢复为 5''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 存量数据换算（幂等：只看 status，重复执行结果一致）
--    启用中（status=1）→ 5 次满额；已停用（status<>1，含锁定的）→ 0 次
-- ------------------------------------------------------------
UPDATE `basic_user` SET `login_error_count` = 5 WHERE `status` = 1;
UPDATE `basic_user` SET `login_error_count` = 0 WHERE `status` <> 1;
