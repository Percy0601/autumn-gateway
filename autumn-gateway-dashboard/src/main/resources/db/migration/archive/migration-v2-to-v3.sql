-- ============================================================
-- 存量库升级脚本：v2 → v3（资源 HTTP 方法支持多选）
-- 适用：autumn-security 库已用 schema-basic-mysql.sql 建过 basic_resource 表的情况
-- 执行：mysql -u<user> -p autumn-security < migration-v2-to-v3.sql
-- 说明：每一步都做了存在性判断，可重复执行
-- ============================================================

SET @db := DATABASE();

-- ------------------------------------------------------------
-- 1. basic_resource.http_method 扩容 16 -> 64
--    HTTP 方法改为多选，存量值仍是单个方法（如 GET / ALL），
--    与新格式（逗号分隔，如 GET,POST）完全兼容，无需回填数据。
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'basic_resource'
                  AND COLUMN_NAME = 'http_method'
                  AND CHARACTER_MAXIMUM_LENGTH < 64);
SET @ddl := IF(@exists > 0,
    'ALTER TABLE `basic_resource` MODIFY `http_method` VARCHAR(64) DEFAULT ''ALL'' COMMENT ''HTTP 方法，支持多选逗号分隔，如 GET,POST；ALL 表示全部''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 校验（应返回 maximum_length = 64）
-- ------------------------------------------------------------
-- SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, COLUMN_COMMENT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'basic_resource'
--    AND COLUMN_NAME = 'http_method';
