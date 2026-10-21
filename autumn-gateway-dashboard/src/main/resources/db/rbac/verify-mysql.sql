-- ================================================================
-- Autumn Gateway Dashboard — RBAC 初始化检查脚本（只读，MySQL 8.0+）
--
-- 用途：自举之后（或每次发版前）跑一遍，确认权限体系是完整的。
--       任何一项「应为空 / 应为 0」的结果不为空，就说明初始化有问题。
--
-- 用法：
--   mysql -uroot -p autumn-security < db/rbac/verify-mysql.sql
--   docker exec -i mysql mysql -uroot -p123456 autumn-security < db/rbac/verify-mysql.sql
-- ================================================================

-- ---------- 1. 数据规模总览（粗判是否跑过初始化） ----------
-- 期望：menu ≥ 40（含按钮权限点）、resource ≥ 25、role ≥ 5、role_permission ≥ 80
SELECT 'basic_namespace'       AS t, COUNT(*) AS cnt FROM `basic_namespace`
UNION ALL SELECT 'basic_app',             COUNT(*) FROM `basic_app`
UNION ALL SELECT 'basic_menu',            COUNT(*) FROM `basic_menu` WHERE `del_flag` = 0
UNION ALL SELECT 'basic_menu(按钮type=3)', COUNT(*) FROM `basic_menu` WHERE `del_flag` = 0 AND `menu_type` = 3
UNION ALL SELECT 'basic_resource',        COUNT(*) FROM `basic_resource` WHERE `status` = 1
UNION ALL SELECT 'basic_role',            COUNT(*) FROM `basic_role` WHERE `del_flag` = 0
UNION ALL SELECT 'basic_role_permission', COUNT(*) FROM `basic_role_permission` WHERE `del_flag` = 0
UNION ALL SELECT 'basic_user',            COUNT(*) FROM `basic_user`
UNION ALL SELECT 'basic_group',           COUNT(*) FROM `basic_group`;


-- ---------- 2. 菜单树完整性（应为空） ----------
-- 2.1 孤儿菜单：parent_id 指向不存在的节点
SELECT 'orphan_menu' AS problem, m.`id`, m.`menu_name`, m.`parent_id`
  FROM `basic_menu` m
  LEFT JOIN `basic_menu` p ON p.`id` = m.`parent_id`
 WHERE m.`parent_id` <> 0 AND p.`id` IS NULL;

-- 2.2 按钮没有挂在菜单下
SELECT 'orphan_button' AS problem, m.`id`, m.`menu_name`
  FROM `basic_menu` m
  LEFT JOIN `basic_menu` p ON p.`id` = m.`parent_id`
 WHERE m.`menu_type` = 3 AND p.`id` IS NULL;

-- 2.3 权限标识缺失或重复（权限标识是 @PreAuthorize 的比对依据，不能为空/重复）
SELECT 'dup_identification' AS problem, m.`menu_identification`, COUNT(*) AS cnt
  FROM `basic_menu` m
 WHERE m.`del_flag` = 0 AND m.`menu_identification` <> ''
 GROUP BY m.`menu_identification`
HAVING COUNT(*) > 1;

-- 2.4 权限标识格式不规范（应为 模块:资源:动作，至少一个冒号）——应为空
SELECT 'bad_identification' AS problem, m.`id`, m.`menu_name`, m.`menu_identification`
  FROM `basic_menu` m
 WHERE m.`del_flag` = 0
   AND m.`menu_identification` NOT LIKE '%:%';


-- ---------- 3. 资源与 app_code 一致性 ----------
-- 3.1 app_code 残留 'admin'（应为 0）——否则打开 URL 鉴权后所有人 403
SELECT 'app_code_admin_left' AS problem,
       (SELECT COUNT(*) FROM `basic_app` WHERE `app_code` = 'admin')      AS app_cnt,
       (SELECT COUNT(*) FROM `basic_resource` WHERE `app_code` = 'admin') AS resource_cnt;

-- 3.2 资源所属 app_id 与 app_code 对不上（应为空）
SELECT 'resource_app_mismatch' AS problem, r.`id`, r.`name`, r.`app_id`, r.`app_code`
  FROM `basic_resource` r
  LEFT JOIN `basic_app` a ON a.`id` = r.`app_id` AND a.`app_code` = r.`app_code`
 WHERE r.`status` = 1 AND a.`id` IS NULL;


-- ---------- 4. 授权链路完整性 ----------
-- 4.1 每个角色的权限点数量
SELECT r.`id`, r.`code`, r.`name`, r.`master`, r.`status`,
       SUM(CASE WHEN p.`type` = 1 THEN 1 ELSE 0 END) AS menu_perm_cnt,
       SUM(CASE WHEN p.`type` = 2 THEN 1 ELSE 0 END) AS resource_perm_cnt
  FROM `basic_role` r
  LEFT JOIN `basic_role_permission` p
         ON p.`role_id` = r.`id` AND p.`del_flag` = 0
 WHERE r.`del_flag` = 0
 GROUP BY r.`id`, r.`code`, r.`name`, r.`master`, r.`status`
 ORDER BY r.`id`;

-- 4.2 有角色但一条权限都没授（非超管角色出现在这里就是漏配）
SELECT 'role_without_permission' AS problem, r.`id`, r.`code`, r.`name`
  FROM `basic_role` r
 WHERE r.`del_flag` = 0 AND r.`master` <> 1
   AND NOT EXISTS (SELECT 1 FROM `basic_role_permission` p
                    WHERE p.`role_id` = r.`id` AND p.`del_flag` = 0);

-- 4.3 每个用户经由「直接角色 + 用户组角色（含上级组继承）」得到的最终角色
SELECT u.`id`, u.`username`, u.`admin`, u.`status`,
       GROUP_CONCAT(DISTINCT r.`code` ORDER BY r.`code`) AS effective_roles
  FROM `basic_user` u
  LEFT JOIN `basic_user_role` ur ON ur.`user_id` = u.`id` AND ur.`del_flag` = 0
  LEFT JOIN `basic_role` d       ON d.`id` = ur.`role_id` AND d.`del_flag` = 0
  LEFT JOIN `basic_user_group` ug ON ug.`user_id` = u.`id` AND ug.`deleted` = 0
  LEFT JOIN `basic_group_group` gg ON gg.`parent_group_id` = ug.`group_id` AND gg.`deleted` = 0
  LEFT JOIN `basic_role_group` rg ON rg.`group_id` IN (ug.`group_id`, gg.`group_id`) AND rg.`deleted` = 0
  LEFT JOIN `basic_role` g       ON g.`id` = rg.`role_id` AND g.`del_flag` = 0
  LEFT JOIN `basic_role` r       ON r.`id` IN (d.`id`, g.`id`)
 GROUP BY u.`id`, u.`username`, u.`admin`, u.`status`
 ORDER BY u.`id`;

-- 4.4 没有任何角色的普通用户（非 admin）——这些人登录后将无权限可用
SELECT 'user_without_role' AS problem, u.`id`, u.`username`
  FROM `basic_user` u
 WHERE u.`admin` <> 1
   AND NOT EXISTS (SELECT 1 FROM `basic_user_role` ur WHERE ur.`user_id` = u.`id` AND ur.`del_flag` = 0)
   AND NOT EXISTS (SELECT 1 FROM `basic_user_group` ug WHERE ug.`user_id` = u.`id` AND ug.`deleted` = 0);


-- ---------- 5. 登录可用性 ----------
-- 5.1 没有密码的账号（无法登录）——应为空
SELECT 'user_without_password' AS problem, u.`id`, u.`username`
  FROM `basic_user` u
 WHERE u.`password` IS NULL OR u.`password` = '';

-- 5.2 剩余重试次数已耗尽但仍是启用状态的账号（login_error_count 减到 0 却未被禁用）——应为空
SELECT 'locked_but_enabled' AS problem, u.`id`, u.`username`, u.`login_error_count`
  FROM `basic_user` u
 WHERE u.`status` = 1 AND u.`login_error_count` <= 0;


-- ---------- 6. 本次新增：重置密码权限点 ----------
-- 6.1 权限点是否存在（应有 1 行）
SELECT 'reset_pwd_menu' AS check_item, m.`id`, m.`menu_identification`, m.`remark`
  FROM `basic_menu` m WHERE m.`id` = 1308;

-- 6.2 哪些角色拥有重置密码权限（超管 master=1 恒拥有，不在此列）
SELECT 'reset_pwd_roles' AS check_item, r.`id`, r.`code`, r.`name`
  FROM `basic_role` r
 WHERE EXISTS (SELECT 1 FROM `basic_role_permission` p
                WHERE p.`role_id` = r.`id` AND p.`del_flag` = 0
                  AND p.`type` = 1 AND p.`ref_id` = 1308);
