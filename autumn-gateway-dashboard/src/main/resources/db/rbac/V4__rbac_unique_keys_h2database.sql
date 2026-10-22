-- ================================================================
-- Autumn Gateway Dashboard — RBAC 唯一键补齐（H2 版）
--
-- 对应 MySQL 版：db/rbac/V4__rbac_unique_keys_mysql.sql
-- 适用：H2 TCP 实例 autumn_security（MODE=MySQL），用 sa 执行（H2 无独立 root）
--
-- 为什么单独一个文件：
--   V4__rbac_fix_and_grant 全部 NOT EXISTS 判重，功能上不依赖唯一键；
--   但唯一键是长期数据卫生的保障——没有它，任何人手写
--   INSERT ... ON DUPLICATE KEY UPDATE 都会静默失效、产生重复授权行。
--
-- 与 MySQL 版的两处改写：
--   1. MySQL 的 `DELETE p FROM t p JOIN (...) k` 多表删除在 H2 不支持，
--      改用窗口函数 ROW_NUMBER() 定位重复行（保留 id 最大的一条）。
--   2. MySQL 用 information_schema 判断键是否存在；H2 直接
--      ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS（幂等）。
--
-- 幂等：可反复执行。
-- 执行：
--   java -cp D:\opt\h2\bin\h2-2.5.252.jar org.h2.tools.Shell ^
--        -url "jdbc:h2:tcp://localhost:9092/autumn_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE" ^
--        -user sa -password 123456 -script <本文件>
-- ================================================================

-- ---------- 1. 清理重复行（保留 id 最大的一条） ----------
DELETE FROM `basic_role_permission`
 WHERE `id` IN (
        SELECT `id` FROM (
            SELECT `id`, ROW_NUMBER() OVER (
                       PARTITION BY `role_id`, `ref_id`, `type` ORDER BY `id` DESC) AS rn
              FROM `basic_role_permission`) d
         WHERE d.rn > 1);

DELETE FROM `basic_user_group`
 WHERE `id` IN (
        SELECT `id` FROM (
            SELECT `id`, ROW_NUMBER() OVER (
                       PARTITION BY `user_id`, `group_id` ORDER BY `id` DESC) AS rn
              FROM `basic_user_group`) d
         WHERE d.rn > 1);

DELETE FROM `basic_role_group`
 WHERE `id` IN (
        SELECT `id` FROM (
            SELECT `id`, ROW_NUMBER() OVER (
                       PARTITION BY `role_id`, `group_id` ORDER BY `id` DESC) AS rn
              FROM `basic_role_group`) d
         WHERE d.rn > 1);


-- ---------- 2. 补唯一键 ----------
ALTER TABLE `basic_role_permission` ADD CONSTRAINT IF NOT EXISTS `uk_role_permission` UNIQUE (`role_id`, `ref_id`, `type`);
ALTER TABLE `basic_user_group`       ADD CONSTRAINT IF NOT EXISTS `uk_user_group`       UNIQUE (`user_id`, `group_id`);
ALTER TABLE `basic_role_group`       ADD CONSTRAINT IF NOT EXISTS `uk_role_group`       UNIQUE (`role_id`, `group_id`);
