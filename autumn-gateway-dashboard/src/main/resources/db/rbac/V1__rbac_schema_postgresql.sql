-- ================================================================
-- Autumn Gateway Dashboard — RBAC 授权链路表结构补齐（PostgreSQL 12+）
--
-- 前置：已执行 schema-basic-postgresql.sql（12 张 basic_* 表已存在）
-- 作用与 MySQL 版 V1 完全等价：补唯一约束 + 索引，业务字段不改。
--
-- 幂等性：
--   PostgreSQL 原生支持 CREATE INDEX IF NOT EXISTS / CREATE UNIQUE INDEX IF NOT EXISTS，
--   直接可重复执行。但 ADD CONSTRAINT 不支持 IF NOT EXISTS，
--   所以唯一约束统一用「唯一索引」实现 —— 效果等价（唯一索引同样阻止重复值，
--   且 ON CONFLICT 依赖的唯一索引可被识别）。
-- ================================================================

-- ----------------------------------------------------------------
-- 1. basic_role_permission —— 角色 → 权限（菜单 / 资源 / 数据范围）
--    唯一索引不含 del_flag：软删后再次授权走 UPDATE 复活同一行。
--    存量重复自检：
--      SELECT role_id, ref_id, type, COUNT(*)
--        FROM basic_role_permission
--       GROUP BY role_id, ref_id, type HAVING COUNT(*) > 1;
-- ----------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_role_permission
    ON basic_role_permission (role_id, ref_id, type);

CREATE INDEX IF NOT EXISTS idx_rp_role_del_type
    ON basic_role_permission (role_id, del_flag, type);

CREATE INDEX IF NOT EXISTS idx_rp_ref_type
    ON basic_role_permission (ref_id, type);


-- ----------------------------------------------------------------
-- 2. basic_user_role —— 用户 → 角色
-- ----------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_role_app
    ON basic_user_role (user_id, role_id, app_id);

CREATE INDEX IF NOT EXISTS idx_ur_user_del
    ON basic_user_role (user_id, del_flag);

CREATE INDEX IF NOT EXISTS idx_ur_role_del
    ON basic_user_role (role_id, del_flag);


-- ----------------------------------------------------------------
-- 3. basic_user_group —— 用户 → 用户组
-- ----------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_group
    ON basic_user_group (user_id, group_id);

CREATE INDEX IF NOT EXISTS idx_ug_user_del
    ON basic_user_group (user_id, deleted);


-- ----------------------------------------------------------------
-- 4. basic_role_group —— 用户组 → 角色
-- ----------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_role_group
    ON basic_role_group (role_id, group_id);

CREATE INDEX IF NOT EXISTS idx_rg_group_del
    ON basic_role_group (group_id, deleted);


-- ----------------------------------------------------------------
-- 5. basic_menu —— 菜单树与按钮标识
-- ----------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_menu_app_parent_sort
    ON basic_menu (app_id, parent_id, sort_num);

CREATE INDEX IF NOT EXISTS idx_menu_identification
    ON basic_menu (menu_identification);


-- ----------------------------------------------------------------
-- 6. basic_resource —— URL 级资源鉴权
-- ----------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_resource_app_status
    ON basic_resource (app_code, status);

-- path 用 btree 前缀模式（PG 10+ 支持 text_pattern_ops 做 LIKE 前缀匹配）
CREATE INDEX IF NOT EXISTS idx_resource_path
    ON basic_resource (path varchar_pattern_ops);


-- ----------------------------------------------------------------
-- 7. basic_app —— 按命名空间查应用
-- ----------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_app_status_ns
    ON basic_app (status, namespace_id);


-- ----------------------------------------------------------------
-- 8. 自检：各表行数
-- ----------------------------------------------------------------
SELECT 'basic_namespace' AS table_name, COUNT(*) AS rows_cnt FROM basic_namespace
UNION ALL SELECT 'basic_app',               COUNT(*) FROM basic_app
UNION ALL SELECT 'basic_menu',              COUNT(*) FROM basic_menu
UNION ALL SELECT 'basic_resource',          COUNT(*) FROM basic_resource
UNION ALL SELECT 'basic_menu_resource',     COUNT(*) FROM basic_menu_resource
UNION ALL SELECT 'basic_role',              COUNT(*) FROM basic_role
UNION ALL SELECT 'basic_role_permission',   COUNT(*) FROM basic_role_permission
UNION ALL SELECT 'basic_user',              COUNT(*) FROM basic_user
UNION ALL SELECT 'basic_user_role',         COUNT(*) FROM basic_user_role
UNION ALL SELECT 'basic_group',             COUNT(*) FROM basic_group
UNION ALL SELECT 'basic_user_group',        COUNT(*) FROM basic_user_group
UNION ALL SELECT 'basic_role_group',        COUNT(*) FROM basic_role_group;
