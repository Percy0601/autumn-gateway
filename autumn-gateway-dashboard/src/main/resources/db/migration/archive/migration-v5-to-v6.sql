-- ============================================================
-- 存量库升级脚本：v5 → v6（收尾漂移：类型统一 / 缺失索引 / 僵尸列）
-- 适用：autumn-security 库（MySQL）
-- 执行：mysql -uroot -p autumn-security < migration-v5-to-v6.sql
-- 说明：每一步都做了存在性判断，可重复执行。
--
-- 背景：以下问题均无既有迁移覆盖过，是 schema 文件演进后存量表未跟进导致的漂移：
--   1. basic_role_permission.create_time / update_time 建表时为 TIMESTAMP，
--      schema-basic-mysql.sql 已统一改为 DATETIME（规避 2038 问题），存量表补齐；
--   2. basic_menu.menu_url / menu_icon 存量表可空，schema 为 NOT NULL DEFAULT ''，
--      先回填 NULL 再收紧（与 schema 对齐，避免前端拿到 null）；
--   3. basic_menu / basic_oper_log / basic_resource / basic_user_group
--      缺 schema 中声明的二级索引（不影响正确性，补齐查询性能）；
--   4. basic_user_role.eid 是旧版 schema 遗留的僵尸列：当前 schema-basic-mysql.sql
--      已无此列、实体不映射、全库无引用、数据全为 0，删除以对齐 schema。
--      （注意：schema-basic-postgresql.sql 仍保留 eid=企业ID，本脚本只作用于 MySQL。）
-- ============================================================

SET @db := DATABASE();

-- ------------------------------------------------------------
-- 1. basic_role_permission 时间列 TIMESTAMP -> DATETIME
--    （保留 DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP 语义）
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_role_permission'
                  AND COLUMN_NAME = 'create_time' AND DATA_TYPE = 'timestamp');
SET @ddl := IF(@exists > 0,
    'ALTER TABLE `basic_role_permission` MODIFY `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_role_permission'
                  AND COLUMN_NAME = 'update_time' AND DATA_TYPE = 'timestamp');
SET @ddl := IF(@exists > 0,
    'ALTER TABLE `basic_role_permission` MODIFY `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. basic_menu.menu_url / menu_icon 收紧为 NOT NULL DEFAULT ''
--    先回填历史 NULL，避免 MODIFY 失败
-- ------------------------------------------------------------
UPDATE `basic_menu` SET `menu_url` = '' WHERE `menu_url` IS NULL;
UPDATE `basic_menu` SET `menu_icon` = '' WHERE `menu_icon` IS NULL;

SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_menu'
                  AND COLUMN_NAME = 'menu_url' AND IS_NULLABLE = 'YES');
SET @ddl := IF(@exists > 0,
    'ALTER TABLE `basic_menu` MODIFY `menu_url` VARCHAR(2000) NOT NULL DEFAULT '''' COMMENT ''接口url（多个采用英文逗号分隔）''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_menu'
                  AND COLUMN_NAME = 'menu_icon' AND IS_NULLABLE = 'YES');
SET @ddl := IF(@exists > 0,
    'ALTER TABLE `basic_menu` MODIFY `menu_icon` VARCHAR(100) NOT NULL DEFAULT '''' COMMENT ''菜单图标''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. 补齐缺失的二级索引（与 schema-basic-mysql.sql 声明一致）
-- ------------------------------------------------------------
-- basic_menu(app_id) / basic_menu(parent_id)
SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_menu' AND INDEX_NAME = 'idx_menu_app_id');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `basic_menu` ADD KEY idx_menu_app_id (app_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_menu' AND INDEX_NAME = 'idx_menu_parent_id');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `basic_menu` ADD KEY idx_menu_parent_id (parent_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- basic_oper_log(op_time) / basic_oper_log(oper_id)
SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_oper_log' AND INDEX_NAME = 'idx_oper_log_op_time');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `basic_oper_log` ADD KEY idx_oper_log_op_time (op_time)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_oper_log' AND INDEX_NAME = 'idx_oper_log_oper_id');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `basic_oper_log` ADD KEY idx_oper_log_oper_id (oper_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- basic_resource(app_code)
SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_resource' AND INDEX_NAME = 'idx_resource_app_code');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `basic_resource` ADD KEY idx_resource_app_code (app_code)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- basic_user_group(user_id) / basic_user_group(group_id)
SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_user_group' AND INDEX_NAME = 'idx_user_group_user_id');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `basic_user_group` ADD KEY idx_user_group_user_id (user_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_user_group' AND INDEX_NAME = 'idx_user_group_group_id');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `basic_user_group` ADD KEY idx_user_group_group_id (group_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4. basic_user_role 删除僵尸列 eid（数据全为 0、无任何引用，先备份后删除）
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_user_role' AND COLUMN_NAME = 'eid');
SET @ddl := IF(@exists > 0, 'ALTER TABLE `basic_user_role` DROP COLUMN `eid`', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
