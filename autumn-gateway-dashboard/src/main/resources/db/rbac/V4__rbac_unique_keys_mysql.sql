-- ================================================================
-- Autumn Gateway Dashboard — RBAC 唯一键补齐（需要 DDL 权限，用 root 执行）
--
-- 为什么单独一个文件：
--   V4__rbac_fix_and_grant_mysql.sql 全部用 NOT EXISTS 判重，无 ALTER 权限也能跑，
--   功能上不依赖唯一键。但唯一键是长期数据卫生的保障——没有它，
--   未来任何人手写 INSERT ... ON DUPLICATE KEY UPDATE 都会静默失效、产生重复授权行。
--
--   应用账号 admin 只有 SELECT/INSERT/UPDATE/DELETE，没有 ALTER，
--   所以这一段必须用 root（102 上 MySQL 跑在 docker 容器里）：
--
--     docker exec -i mysql mysql -uroot -p123456 autumn-security \
--         < db/rbac/V4__rbac_unique_keys_mysql.sql
--
-- 幂等：先清理重复行，再用 information_schema 判断键是否存在，存在则跳过。
--       可反复执行。
-- ================================================================

-- ---------- 0. 重复数据自检（只读，人工可看） ----------
SELECT 'dup_role_permission' AS problem, `role_id`, `ref_id`, `type`, COUNT(*) AS c
  FROM `basic_role_permission`
 GROUP BY `role_id`, `ref_id`, `type` HAVING c > 1;

SELECT 'dup_user_group' AS problem, `user_id`, `group_id`, COUNT(*) AS c
  FROM `basic_user_group`
 GROUP BY `user_id`, `group_id` HAVING c > 1;

SELECT 'dup_role_group' AS problem, `role_id`, `group_id`, COUNT(*) AS c
  FROM `basic_role_group`
 GROUP BY `role_id`, `group_id` HAVING c > 1;


-- ---------- 1. 清理重复行（保留 id 最大的一条，其余软删 / 删除） ----------
-- basic_role_permission：删除重复，保留 MAX(id)
DELETE p
  FROM `basic_role_permission` p
  JOIN (
        SELECT `role_id`, `ref_id`, `type`, MAX(`id`) AS keep_id
          FROM `basic_role_permission`
         GROUP BY `role_id`, `ref_id`, `type`
        HAVING COUNT(*) > 1
       ) k
    ON k.`role_id` = p.`role_id`
   AND k.`ref_id`  = p.`ref_id`
   AND k.`type`    = p.`type`
   AND p.`id` <> k.keep_id;

-- basic_user_group / basic_role_group 没有软删列，直接删重复行
DELETE ug
  FROM `basic_user_group` ug
  JOIN (
        SELECT `user_id`, `group_id`, MAX(`id`) AS keep_id
          FROM `basic_user_group`
         GROUP BY `user_id`, `group_id`
        HAVING COUNT(*) > 1
       ) k
    ON k.`user_id` = ug.`user_id`
   AND k.`group_id` = ug.`group_id`
   AND ug.`id` <> k.keep_id;

DELETE rg
  FROM `basic_role_group` rg
  JOIN (
        SELECT `role_id`, `group_id`, MAX(`id`) AS keep_id
          FROM `basic_role_group`
         GROUP BY `role_id`, `group_id`
        HAVING COUNT(*) > 1
       ) k
    ON k.`role_id` = rg.`role_id`
   AND k.`group_id` = rg.`group_id`
   AND rg.`id` <> k.keep_id;


-- ---------- 2. 唯一键（存在则跳过） ----------
SET @tbl = 'basic_role_permission';
SET @idx = 'uk_role_permission';
SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx),
    'SELECT ''skip uk_role_permission''',
    'ALTER TABLE `basic_role_permission` ADD UNIQUE KEY `uk_role_permission` (`role_id`, `ref_id`, `type`)');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @tbl = 'basic_user_group';
SET @idx = 'uk_user_group';
SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx),
    'SELECT ''skip uk_user_group''',
    'ALTER TABLE `basic_user_group` ADD UNIQUE KEY `uk_user_group` (`user_id`, `group_id`)');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @tbl = 'basic_role_group';
SET @idx = 'uk_role_group';
SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx),
    'SELECT ''skip uk_role_group''',
    'ALTER TABLE `basic_role_group` ADD UNIQUE KEY `uk_role_group` (`role_id`, `group_id`)');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;


-- ---------- 3. 结果确认（期望三行都存在） ----------
SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND index_name IN ('uk_role_permission', 'uk_user_group', 'uk_role_group')
 GROUP BY table_name, index_name, non_unique;
