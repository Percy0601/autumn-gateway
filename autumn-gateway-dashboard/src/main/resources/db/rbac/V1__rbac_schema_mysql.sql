-- ================================================================
-- Autumn Gateway Dashboard — RBAC 授权链路表结构补齐（MySQL 8.0+）
--
-- 前置：已执行 schema-basic-mysql.sql（12 张 basic_* 表已存在）
-- 目标：补齐「用户 → 角色 → 权限 → 菜单/资源/按钮」授权链路
--       所需的唯一约束与索引。业务字段一律不改，纯增量。
--
-- 幂等性说明（重要）：
--   MySQL 8.0 不支持 CREATE INDEX IF NOT EXISTS / ADD CONSTRAINT IF NOT EXISTS。
--   重复执行时 ADD UNIQUE / ADD INDEX 会抛 1061 / 1060 错误。
--   因此本脚本采用「存储过程 + information_schema 探测」的方式实现真正的幂等，
--   可随 spring.sql.init.mode=always 在每次启动时重复执行。
--   执行完毕后自动 DROP 掉临时存储过程，不留痕迹。
--
-- 与 schema-basic-mysql.sql 的关系：
--   那个脚本负责建表（新环境），本脚本负责「收紧约束 + 补索引」（含存量库）。
--   两者顺序不可颠倒。
-- ================================================================

-- ----------------------------------------------------------------
-- 1. 幂等 DDL 辅助过程
--    p_schema       业务库名（用 DATABASE() 动态取，避免硬编码）
--    p_table        表名
--    p_index        索引/唯一键名
--    p_columns      列清单，如 "role_id, ref_id"
--    p_unique       1=唯一索引（含已删除行的软删表慎用唯一键，见下方逐条说明）
-- ----------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_rbac_add_index`;

DELIMITER $$
CREATE PROCEDURE `sp_rbac_add_index`(
    IN p_table   VARCHAR(64),
    IN p_index   VARCHAR(64),
    IN p_columns VARCHAR(255),
    IN p_unique  TINYINT
)
BEGIN
    DECLARE v_exists INT DEFAULT 0;

    SELECT COUNT(1) INTO v_exists
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = p_table
       AND INDEX_NAME   = p_index;

    IF v_exists = 0 THEN
        IF p_unique = 1 THEN
            SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE KEY `', p_index, '` (', p_columns, ')');
        ELSE
            SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD KEY `', p_index, '` (', p_columns, ')');
        END IF;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('[rbac] created index ', p_index, ' on ', p_table) AS msg;
    ELSE
        SELECT CONCAT('[rbac] index ', p_index, ' on ', p_table, ' already exists, skipped') AS msg;
    END IF;
END$$
DELIMITER ;


-- ----------------------------------------------------------------
-- 2. basic_role_permission —— 角色 → 权限（菜单 / 资源 / 数据范围）
--
--    授权判定的核心表。鉴权时按 role_id + type + del_flag 捞 ref_id，
--    必须走 (role_id, del_flag, type) 覆盖索引，否则每次请求全表扫。
--
--    唯一键说明：
--      这里给 (role_id, ref_id, type) 加唯一键 —— 不含 del_flag。
--      原因：同一角色对同一对象只允许一条记录，删除走 del_flag=1 的软删，
--      再次授权时用 UPDATE 复活该行，而不是 INSERT 新行。
--      若历史数据已存在重复行，唯一键会创建失败，先跑下面这段自检：
--
--        SELECT role_id, ref_id, type, COUNT(*) c
--          FROM basic_role_permission
--         GROUP BY role_id, ref_id, type HAVING c > 1;
--
--      有结果则先清理重复（保留 MAX(id)），再执行本脚本。
-- ----------------------------------------------------------------
CALL sp_rbac_add_index('basic_role_permission', 'uk_role_permission', '`role_id`, `ref_id`, `type`', 1);
CALL sp_rbac_add_index('basic_role_permission', 'idx_rp_role_del_type', '`role_id`, `del_flag`, `type`', 0);
CALL sp_rbac_add_index('basic_role_permission', 'idx_rp_ref_type',     '`ref_id`, `type`', 0);


-- ----------------------------------------------------------------
-- 3. basic_user_role —— 用户 → 角色
--
--    注意：真值是 del_flag=0 的行。加唯一键 (user_id, role_id, app_id)
--    同样不含 del_flag，重复授权走 UPDATE 复活。
--
--    存量重复自检：
--        SELECT user_id, role_id, app_id, COUNT(*) c
--          FROM basic_user_role
--         GROUP BY user_id, role_id, app_id HAVING c > 1;
-- ----------------------------------------------------------------
CALL sp_rbac_add_index('basic_user_role', 'uk_user_role_app', '`user_id`, `role_id`, `app_id`', 1);
CALL sp_rbac_add_index('basic_user_role', 'idx_ur_user_del',  '`user_id`, `del_flag`', 0);
CALL sp_rbac_add_index('basic_user_role', 'idx_ur_role_del',  '`role_id`, `del_flag`', 0);


-- ----------------------------------------------------------------
-- 4. basic_user_group —— 用户 → 用户组（岗位）
--
--    授权链路里用户组的角色会被继承，因此 user_id 上的查询是最热的。
--    (user_id, deleted) 覆盖索引让「查这个人的所有组」只走索引不回表。
-- ----------------------------------------------------------------
CALL sp_rbac_add_index('basic_user_group', 'uk_user_group',   '`user_id`, `group_id`', 1);
CALL sp_rbac_add_index('basic_user_group', 'idx_ug_user_del', '`user_id`, `deleted`', 0);


-- ----------------------------------------------------------------
-- 5. basic_role_group —— 用户组 → 角色
--
--    分组层级继承时，会按 group_id 集合批量捞角色，故补 (group_id, deleted)。
-- ----------------------------------------------------------------
CALL sp_rbac_add_index('basic_role_group', 'uk_role_group',   '`role_id`, `group_id`', 1);
CALL sp_rbac_add_index('basic_role_group', 'idx_rg_group_del', '`group_id`, `deleted`', 0);


-- ----------------------------------------------------------------
-- 6. basic_menu —— 菜单树查询与权限前缀匹配
--
--    鉴权时需要按 menu_identification（按钮权限标识）反查，
--    菜单树渲染需要按 (app_id, parent_id, sort_num) 有序取。
-- ----------------------------------------------------------------
CALL sp_rbac_add_index('basic_menu', 'idx_menu_app_parent_sort', '`app_id`, `parent_id`, `sort_num`', 0);
CALL sp_rbac_add_index('basic_menu', 'idx_menu_identification',  '`menu_identification`', 0);


-- ----------------------------------------------------------------
-- 7. basic_resource —— URL 级资源鉴权
--
--    URL 鉴权按 (app_code, status) 过滤后用 path + match_type 匹配。
--    这里不建 path 上的唯一键 —— 同一 path 允许挂不同 http_method。
-- ----------------------------------------------------------------
CALL sp_rbac_add_index('basic_resource', 'idx_resource_app_status', '`app_code`, `status`', 0);
CALL sp_rbac_add_index('basic_resource', 'idx_resource_path',       '`path`(191)', 0);


-- ----------------------------------------------------------------
-- 8. basic_app —— 按命名空间查应用
-- ----------------------------------------------------------------
CALL sp_rbac_add_index('basic_app', 'idx_app_status_ns', '`status`, `namespace_id`', 0);


-- ----------------------------------------------------------------
-- 9. 清理辅助过程
-- ----------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_rbac_add_index`;


-- ----------------------------------------------------------------
-- 10. 自检：打印授权链路上各表的行数，便于确认初始化是否生效
--     （只读，不影响数据）
-- ----------------------------------------------------------------
SELECT 'basic_namespace'         AS table_name, COUNT(*) AS rows_cnt FROM `basic_namespace`
UNION ALL SELECT 'basic_app',               COUNT(*) FROM `basic_app`
UNION ALL SELECT 'basic_menu',              COUNT(*) FROM `basic_menu`
UNION ALL SELECT 'basic_resource',          COUNT(*) FROM `basic_resource`
UNION ALL SELECT 'basic_menu_resource',     COUNT(*) FROM `basic_menu_resource`
UNION ALL SELECT 'basic_role',              COUNT(*) FROM `basic_role`
UNION ALL SELECT 'basic_role_permission',   COUNT(*) FROM `basic_role_permission`
UNION ALL SELECT 'basic_user',              COUNT(*) FROM `basic_user`
UNION ALL SELECT 'basic_user_role',         COUNT(*) FROM `basic_user_role`
UNION ALL SELECT 'basic_group',             COUNT(*) FROM `basic_group`
UNION ALL SELECT 'basic_user_group',        COUNT(*) FROM `basic_user_group`
UNION ALL SELECT 'basic_role_group',        COUNT(*) FROM `basic_role_group`;
