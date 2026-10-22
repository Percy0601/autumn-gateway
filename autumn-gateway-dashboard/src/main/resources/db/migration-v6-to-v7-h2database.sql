-- ============================================================
-- 存量库升级脚本：v6 → v7（basic_user.login_error_count 语义反转）—— H2 版
--
-- 对应 MySQL 版：db/migration-v6-to-v7.sql
-- 适用：H2 TCP 实例 autumn_security（MODE=MySQL）
--
-- 与 MySQL 版的差异：
--   1. MySQL 用 information_schema + PREPARE/EXECUTE 做「列存在才 ALTER」的动态 DDL；
--      H2 的 PREPARE 不支持 DDL 拼接，改为直接 ALTER（本库必存在该列），
--      若目标库确实没有该列，H2 会报错，此时请先跑 schema-basic-h2.sql。
--   2. 注释改写：H2 的 ALTER COLUMN 不支持 COMMENT，列注释只能随建表脚本维护
--      （schema-basic-h2.sql 里的 login_error_count 已改为 DEFAULT 5）。
--
-- 背景：login_error_count 原为「连续登录失败次数」（0 = 正常，达到
--       autumn.user.max-login-error-count 即禁用）。现统一改为「剩余登录重试次数」：
--         新建/改密/启用 → 5（满额）；密码错误 → -1；减到 0 → 自动禁用账号。
--
-- 执行：
--   java -cp D:\opt\h2\bin\h2-2.5.252.jar org.h2.tools.Shell ^
--        -url "jdbc:h2:tcp://localhost:9092/autumn_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE" ^
--        -user sa -password 123456 -script <本文件>
--
-- 幂等：可重复执行。
-- ============================================================

-- ---------- 1. 列默认值 DEFAULT 0 -> DEFAULT 5 ----------
ALTER TABLE `basic_user` ALTER COLUMN `login_error_count` SET DEFAULT 5;

-- ---------- 2. 存量数据换算（只看 status，重复执行结果一致） ----------
UPDATE `basic_user` SET `login_error_count` = 5 WHERE `status` = 1;
UPDATE `basic_user` SET `login_error_count` = 0 WHERE `status` <> 1;
