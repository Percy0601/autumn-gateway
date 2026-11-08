-- ============================================================
-- 存量库升级脚本：v3 → v4（引入命名空间 basic_namespace）
-- 适用：autumn-security 库已用旧 schema 建表的情况
-- 执行：mysql -u<user> -p autumn-security < migration-v3-to-v4.sql
-- 说明：每一步都做了存在性判断，可重复执行。
--
-- 注意：
--   1. basic_namespace 表由应用启动时 spring.sql.init（schema-basic-mysql.sql 的
--      CREATE TABLE IF NOT EXISTS）自动创建，本脚本无需再建。
--   2. 默认租户（code=default）的插入、以及存量 basic_app / basic_role 的
--      namespace_id 回填，由应用启动时的初始化器（NamespaceDataInitializer）
--      自动完成，本脚本只负责 DDL 结构变更。
--   3. 执行完本脚本后，请用具备 ALTER 权限的账号重启 autumn-gateway-dashboard 服务。
-- ============================================================

SET @db := DATABASE();

-- ------------------------------------------------------------
-- 1. basic_app 新增命名空间字段
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_app' AND COLUMN_NAME = 'namespace_id');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `basic_app` ADD COLUMN namespace_id BIGINT DEFAULT NULL COMMENT ''所属命名空间ID，关联 basic_namespace.id'' AFTER status',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_app' AND COLUMN_NAME = 'namespace_code');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `basic_app` ADD COLUMN namespace_code VARCHAR(64) DEFAULT NULL COMMENT ''所属命名空间编码'' AFTER namespace_id',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_app' AND INDEX_NAME = 'idx_app_namespace');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `basic_app` ADD KEY idx_app_namespace (namespace_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. basic_role 移除 app_id，新增命名空间字段（角色改为跨应用、归属命名空间）
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_role' AND COLUMN_NAME = 'app_id');
SET @ddl := IF(@exists > 0, 'ALTER TABLE `basic_role` DROP COLUMN app_id', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_role' AND COLUMN_NAME = 'namespace_id');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `basic_role` ADD COLUMN namespace_id BIGINT NOT NULL DEFAULT 0 COMMENT ''命名空间ID，关联 basic_namespace.id'' AFTER id',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_role' AND COLUMN_NAME = 'namespace_code');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `basic_role` ADD COLUMN namespace_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''命名空间编码'' AFTER namespace_id',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_role' AND INDEX_NAME = 'idx_role_namespace_id');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `basic_role` ADD KEY idx_role_namespace_id (namespace_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. basic_group 新增命名空间字段（岗位改为归属命名空间）
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_group' AND COLUMN_NAME = 'namespace_id');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `basic_group` ADD COLUMN namespace_id BIGINT DEFAULT NULL COMMENT ''所属命名空间ID，关联 basic_namespace.id'' AFTER id',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_group' AND COLUMN_NAME = 'namespace_code');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `basic_group` ADD COLUMN namespace_code VARCHAR(64) DEFAULT NULL COMMENT ''所属命名空间编码'' AFTER namespace_id',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_group' AND INDEX_NAME = 'basic_group_namespace_id_IDX');
SET @ddl := IF(@exists = 0, 'ALTER TABLE `basic_group` ADD KEY basic_group_namespace_id_IDX (namespace_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
