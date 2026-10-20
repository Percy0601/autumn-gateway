-- ================================================================
-- Autumn Gateway Dashboard — RBAC 授权补充脚本 V3（MySQL 8.0+）
--
-- 前置：V1__rbac_schema_mysql.sql（索引/唯一键）→ V2__rbac_init_data_mysql.sql（菜单/资源/角色）
--
-- 本脚本做三件事：
--   1) 收敛 app_code 二义性（'admin' → 'dashboard'）
--   2) 补齐「重置密码」按钮对应的 API 资源与菜单-资源关联
--   3) 把「重置密码」授权给系统管理员角色
--
-- 幂等性：全部 INSERT ... ON DUPLICATE KEY UPDATE / 条件 UPDATE，可重复执行。
-- ================================================================

SET @now = NOW();

-- ================================================================
-- 1. 收敛 app_code
--
--    历史问题：Java 侧的 NamespaceDataInitializer 播种的是 app_code='admin'，
--    而 V2 脚本与 PermissionService 的默认配置（basic.rbac.app-code）用的是 'dashboard'。
--    两者并存时，SQL_RESOURCES 里 `r.app_code = 'dashboard'` 匹配不到任何资源
--    —— 表现为「打开 URL 鉴权后所有人 403」。这里统一收敛到 'dashboard'。
--
--    注意：V2 脚本里 basic_app id=1 已经是 'dashboard'（ON DUPLICATE KEY UPDATE 会把
--    存量 'admin' 改写掉），所以这段主要是兜底清理 basic_resource 与遗漏行。
-- ================================================================
UPDATE `basic_app`
   SET `app_code` = 'dashboard', `updated_at` = @now
 WHERE `app_code` = 'admin';

UPDATE `basic_resource`
   SET `app_code` = 'dashboard', `updated_at` = @now
 WHERE `app_code` = 'admin';


-- ================================================================
-- 2. 「重置密码」的 API 资源 + 菜单-资源关联
--
--    菜单按钮 1308（system:user:reset-pwd）在 V2 里已建好，当时标注为
--    「预留：暂未实现接口」。本次接口落地，把 remark 改成实际路径，
--    并补上对应的 basic_resource 与 basic_menu_resource。
--
--    ⚠️ 路径粒度说明：
--      重置密码的 URL 是 PUT /api/system/basic/user/{id}/reset-password，
--      与「编辑用户」资源 2012（PUT + prefix '/api/system/basic/user'）在 URL 层重叠，
--      prefix 匹配无法区分二者。因此：
--        · URL 层（basic_resource）只能做到「能调用户写接口」这一档；
--        · 「谁可以重置密码」的细粒度判定由方法级 @PreAuthorize('system:user:reset-pwd') 负责。
--      这是本项目把细粒度管控下沉到方法级的主要原因之一。
-- ================================================================
INSERT INTO `basic_resource` (`id`, `app_id`, `app_code`, `name`, `path`, `http_method`, `match_type`, `description`, `status`, `created_at`, `updated_at`)
VALUES
 (2016, 1, 'dashboard', '重置密码', '/api/system/basic/user', 'PUT', 'prefix',
  'PUT /api/system/basic/user/{id}/reset-password；与 2012 重叠，细粒度以权限码 system:user:reset-pwd 为准', 1, @now, @now)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `path` = VALUES(`path`),
                        `http_method` = VALUES(`http_method`), `match_type` = VALUES(`match_type`),
                        `description` = VALUES(`description`), `status` = 1,
                        `app_code` = 'dashboard', `app_id` = 1, `updated_at` = @now;

-- 按钮 1308 → 资源 2016
INSERT INTO `basic_menu_resource` (`menu_id`, `resource_id`, `deleted`, `create_time`, `update_time`, `op_user_id`)
VALUES (1308, 2016, 0, @now, @now, 1)
ON DUPLICATE KEY UPDATE `deleted` = 0, `update_time` = @now, `op_user_id` = 1;

-- 更新菜单 1308 的备注：从「预留」改为「已实现」
UPDATE `basic_menu`
   SET `remark` = 'PUT /api/system/basic/user/{id}/reset-password（已实现）', `update_time` = @now
 WHERE `id` = 1308;


-- ================================================================
-- 3. 角色授权：把「重置密码」授予系统管理员（role_id=2）
--
--    V2 里 SYS_ADMIN 刻意排除了 1303（删除用户）与 1308（重置密码），
--    理由是「高危，单独授予」。本次重置密码功能落地，这里放开 1308 ——
--    系统管理员的日常运维就是帮用户重置密码，全部压给超管不现实。
--
--    如要让「重置密码」仅限超级管理员，执行下面这条回收语句即可：
--      UPDATE basic_role_permission SET del_flag = 1, update_time = NOW()
--       WHERE role_id = 2 AND type = 1 AND ref_id = 1308;
--      UPDATE basic_role_permission SET del_flag = 1, update_time = NOW()
--       WHERE role_id = 2 AND type = 2 AND ref_id = 2016;
--
--    安全管理员（role_id=3）按职责分离原则不授予重置密码权限。
-- ================================================================
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
VALUES
 (2, 1308, 0, 1, 1, @now, @now),   -- 菜单权限：重置密码按钮
 (2, 2016, 0, 2, 1, @now, @now)    -- 资源权限：重置密码接口
ON DUPLICATE KEY UPDATE `del_flag` = 0, `update_time` = @now, `op_user_id` = 1;


-- ================================================================
-- 4. 一致性自检（只读，人工核对）
-- ================================================================

-- 4.1 重置密码权限点是否就位（应有 1308 / system:user:reset-pwd）
SELECT m.`id`, m.`menu_name`, m.`menu_identification`, m.`remark`
  FROM `basic_menu` m
 WHERE m.`id` = 1308;

-- 4.2 哪些角色拥有「重置密码」（sysadmin 应命中；security_admin / auditor / viewer 不应命中）
SELECT r.`id`, r.`code`, r.`name`, r.`master`
  FROM `basic_role` r
 WHERE r.`del_flag` = 0
   AND EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`del_flag` = 0
           AND p.`type` = 1 AND p.`ref_id` = 1308)
UNION
SELECT r.`id`, r.`code`, r.`name`, r.`master`
  FROM `basic_role` r
 WHERE r.`master` = 1 AND r.`del_flag` = 0;

-- 4.3 app_code 是否还有残留的 'admin'（应为 0）
SELECT (SELECT COUNT(*) FROM `basic_app` WHERE `app_code` = 'admin')      AS app_admin_cnt,
       (SELECT COUNT(*) FROM `basic_resource` WHERE `app_code` = 'admin') AS resource_admin_cnt,
       (SELECT COUNT(*) FROM `basic_app` WHERE `app_code` = 'dashboard')  AS app_dashboard_cnt;
