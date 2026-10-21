-- ================================================================
-- Autumn Gateway Dashboard — RBAC 修复与授权补齐（MySQL 8.0+）
--
-- 背景（2026-10-21 现场诊断结论）：
--   线上 autumn-security 库里 basic_menu 只有 13 条、按钮权限点（menu_type=3）为 0、
--   basic_resource 仅 2 条、basic_menu_resource 为 0、basic_role_permission 仅 3 条。
--   更关键的是：现有 menu_identification 是单段的 `user` / `role` / `menu`，
--   而代码 PermCodes 用的是三段权限码 `system:user:create`。
--
--   由此产生的症状：
--     PermissionService#allMenuIdentifications 读的是 basic_menu.menu_identification，
--     超管（admin=1 / master=1）的 authorities 被装配成 {user, role, menu, ...}，
--     而 @PreAuthorize 要的是 `system:role:assign-menu` —— 字符串精确比对失败，
--     于是「超管也点不动授权按钮」。同理前端 v-auth 指令也全部不显示。
--
-- 本脚本做什么：
--   1. 把现有菜单的 menu_identification 规范化为三段权限码（与 PermCodes.java 严格一致）
--   2. 补齐 41 个按钮权限点（menu_type=3）
--   3. 补齐 URL 资源（basic_resource），并把 app_code 从 'admin' 收敛为 'dashboard'
--      （否则 basic.rbac.app-code=dashboard 永远匹配不上，开 url-check 会全 403）
--   4. 补齐 菜单↔资源 关联（basic_menu_resource）
--   5. 给 master=1 的角色（admin / admiin）授予全量菜单 + 按钮 + 资源；
--      给 master<>1 的角色（auditor）授予只读套餐
--   6. 修正 basic_user.login_error_count（当前为 0，会被判定为已耗尽）
--   7. 清理 basic_user_role 里 user_id 不存在的脏数据
--
-- 设计取舍：
--   · 不引入 V2 的第二套 ID 段（1000~1399）。当前库已存在 1549~1562 的菜单，
--     再跑 V2 会同时存在两棵树，前端出现两份同名菜单、权限码也对不上
--     （见 docs/权限与授权设计.md §8.3）。本脚本就地升级现有数据。
--   · 全部 INSERT 用 NOT EXISTS 判重，不依赖唯一键，
--     因此无 ALTER 权限的应用账号（如 admin）也能重复执行。
--   · 需要 DDL 权限的唯一键补齐（uk_role_permission / uk_user_group / uk_role_group）
--     单独放在 V4__rbac_fix_grant_ddl_mysql.sql，用 root 执行一次即可。
--
-- 执行后：重启 dashboard 或等 basic.rbac.cache-ttl-seconds（默认 60s）缓存过期。
-- ================================================================

SET @now = NOW();

-- ================================================================
-- 1. 应用：app_code 收敛为 dashboard
--    basic.rbac.app-code=dashboard，而资源表的 app_code 还是 'admin'，
--    PermissionService#SQL_RESOURCES 里 r.app_code = ? 会永远匹配不上。
-- ================================================================
UPDATE `basic_app`
   SET `app_code` = 'dashboard', `name` = '网关控制台', `updated_at` = @now
 WHERE `id` = 5 AND `app_code` = 'admin';

UPDATE `basic_resource`
   SET `app_code` = 'dashboard'
 WHERE `app_code` = 'admin';


-- ================================================================
-- 2. 菜单权限标识规范化（关键修复）
--    单段 `user` → 三段 `system:user:view`，与 PermCodes.java 完全对齐。
--    1549/1552 属于 order 演示应用，保持独立前缀，不混入 dashboard 权限体系。
-- ================================================================
UPDATE `basic_menu`
   SET `menu_identification` =
       CASE `id`
           WHEN 1549 THEN 'order:root:view'
           WHEN 1550 THEN 'demo:app:view'      -- 与 1554「应用管理」重复的演示遗留节点，保留可见但不参与鉴权
           WHEN 1552 THEN 'order:list:view'
           WHEN 1553 THEN 'system:root:view'
           WHEN 1554 THEN 'system:app:view'
           WHEN 1555 THEN 'system:user:view'
           WHEN 1556 THEN 'system:role:view'
           WHEN 1557 THEN 'system:menu:view'
           WHEN 1558 THEN 'system:resource:view'
           WHEN 1559 THEN 'system:group:view'
           WHEN 1560 THEN 'dashboard:home:view'
           WHEN 1561 THEN 'system:namespace:view'
           WHEN 1562 THEN 'system:log:view'
           ELSE `menu_identification`
       END,
       `update_time` = @now
 WHERE `id` IN (1549, 1550, 1552, 1553, 1554, 1555, 1556, 1557, 1558, 1559, 1560, 1561, 1562);

-- 规范化 sort_num（现有全为 0，菜单顺序不稳定）
UPDATE `basic_menu`
   SET `sort_num` =
       CASE `id`
           WHEN 1549 THEN 5
           WHEN 1550 THEN 90
           WHEN 1552 THEN 10
           WHEN 1553 THEN 20
           WHEN 1554 THEN 60
           WHEN 1555 THEN 10
           WHEN 1556 THEN 20
           WHEN 1557 THEN 40
           WHEN 1558 THEN 50
           WHEN 1559 THEN 30
           WHEN 1560 THEN 1
           WHEN 1561 THEN 70
           WHEN 1562 THEN 80
           ELSE `sort_num`
       END
 WHERE `id` IN (1549, 1550, 1552, 1553, 1554, 1555, 1556, 1557, 1558, 1559, 1560, 1561, 1562);


-- ================================================================
-- 3. 按钮权限点（menu_type=3，41 个）
--    ID 段 1570~1649，避开现有 1549~1562 并留白。
--    menu_identification 与 PermCodes.java 常量逐一对齐，可全局检索核对。
-- ================================================================
INSERT INTO `basic_menu`
  (`id`, `app_id`, `parent_id`, `menu_type`, `menu_name`, `menu_url`, `menu_identification`,
   `menu_icon`, `sort_num`, `menu_status`, `del_flag`, `create_user`, `create_time`, `update_user`, `update_time`, `remark`)
VALUES
  -- 用户管理（父 1555）
  (1570, 5, 1555, 3, '新增用户',   '', 'system:user:create',       '', 10, 1, 0, 1, @now, 1, @now, 'POST /api/system/basic/user'),
  (1571, 5, 1555, 3, '编辑用户',   '', 'system:user:update',       '', 20, 1, 0, 1, @now, 1, @now, 'PUT /api/system/basic/user/{id}'),
  (1572, 5, 1555, 3, '删除用户',   '', 'system:user:delete',       '', 30, 1, 0, 1, @now, 1, @now, 'DELETE /api/system/basic/user/{id}'),
  (1573, 5, 1555, 3, '启用用户',   '', 'system:user:enable',       '', 40, 1, 0, 1, @now, 1, @now, 'PUT .../user/{id}/enable'),
  (1574, 5, 1555, 3, '停用用户',   '', 'system:user:disable',      '', 50, 1, 0, 1, @now, 1, @now, 'PUT .../user/{id}/disable'),
  (1575, 5, 1555, 3, '分配角色',   '', 'system:user:assign-role',  '', 60, 1, 0, 1, @now, 1, @now, 'PUT .../user/{id}/roles'),
  (1576, 5, 1555, 3, '分配用户组', '', 'system:user:assign-group', '', 70, 1, 0, 1, @now, 1, @now, 'PUT .../user/{id}/groups'),
  (1577, 5, 1555, 3, '重置密码',   '', 'system:user:reset-pwd',    '', 80, 1, 0, 1, @now, 1, @now, 'PUT .../user/{id}/reset-password'),
  -- 角色管理（父 1556）
  (1580, 5, 1556, 3, '新增角色',   '', 'system:role:create',         '', 10, 1, 0, 1, @now, 1, @now, 'POST /api/system/basic/role'),
  (1581, 5, 1556, 3, '编辑角色',   '', 'system:role:update',         '', 20, 1, 0, 1, @now, 1, @now, 'PUT /api/system/basic/role/{id}'),
  (1582, 5, 1556, 3, '启用角色',   '', 'system:role:enable',         '', 30, 1, 0, 1, @now, 1, @now, ''),
  (1583, 5, 1556, 3, '停用角色',   '', 'system:role:disable',        '', 40, 1, 0, 1, @now, 1, @now, ''),
  (1584, 5, 1556, 3, '分配用户',   '', 'system:role:assign-user',    '', 50, 1, 0, 1, @now, 1, @now, 'PUT .../role/{id}/users'),
  (1585, 5, 1556, 3, '分配菜单',   '', 'system:role:assign-menu',    '', 60, 1, 0, 1, @now, 1, @now, 'PUT .../role/{id}/menus'),
  (1586, 5, 1556, 3, '分配资源',   '', 'system:role:assign-resource','', 70, 1, 0, 1, @now, 1, @now, 'PUT .../role/{id}/resources'),
  (1587, 5, 1556, 3, '分配用户组', '', 'system:role:assign-group',   '', 80, 1, 0, 1, @now, 1, @now, 'PUT .../role/{id}/groups'),
  -- 用户组管理（父 1559）
  (1590, 5, 1559, 3, '新增用户组', '', 'system:group:create',        '', 10, 1, 0, 1, @now, 1, @now, 'POST /api/system/basic/group'),
  (1591, 5, 1559, 3, '编辑用户组', '', 'system:group:update',        '', 20, 1, 0, 1, @now, 1, @now, 'PUT /api/system/basic/group/{id}'),
  (1592, 5, 1559, 3, '删除用户组', '', 'system:group:delete',        '', 30, 1, 0, 1, @now, 1, @now, 'DELETE /api/system/basic/group/{id}'),
  (1593, 5, 1559, 3, '启用用户组', '', 'system:group:enable',        '', 40, 1, 0, 1, @now, 1, @now, ''),
  (1594, 5, 1559, 3, '停用用户组', '', 'system:group:disable',       '', 50, 1, 0, 1, @now, 1, @now, ''),
  (1595, 5, 1559, 3, '分配用户',   '', 'system:group:assign-user',   '', 60, 1, 0, 1, @now, 1, @now, 'PUT .../group/{id}/users'),
  (1596, 5, 1559, 3, '分配角色',   '', 'system:group:assign-role',   '', 70, 1, 0, 1, @now, 1, @now, 'PUT .../group/{id}/roles'),
  (1597, 5, 1559, 3, '维护层级',   '', 'system:group:assign-sub',    '', 80, 1, 0, 1, @now, 1, @now, 'PUT .../group/{id}/sub-groups'),
  -- 菜单管理（父 1557）
  (1600, 5, 1557, 3, '新增菜单',   '', 'system:menu:create',        '', 10, 1, 0, 1, @now, 1, @now, 'POST /api/system/basic/menu'),
  (1601, 5, 1557, 3, '编辑菜单',   '', 'system:menu:update',        '', 20, 1, 0, 1, @now, 1, @now, 'PUT /api/system/basic/menu/{id}'),
  (1602, 5, 1557, 3, '启用菜单',   '', 'system:menu:enable',        '', 30, 1, 0, 1, @now, 1, @now, ''),
  (1603, 5, 1557, 3, '停用菜单',   '', 'system:menu:disable',       '', 40, 1, 0, 1, @now, 1, @now, ''),
  (1604, 5, 1557, 3, '关联资源',   '', 'system:menu:assign-resource','', 50, 1, 0, 1, @now, 1, @now, 'PUT .../menu/{id}/resources'),
  -- 资源管理（父 1558）
  (1610, 5, 1558, 3, '新增资源',   '', 'system:resource:create',    '', 10, 1, 0, 1, @now, 1, @now, 'POST /api/system/basic/resource'),
  (1611, 5, 1558, 3, '编辑资源',   '', 'system:resource:update',    '', 20, 1, 0, 1, @now, 1, @now, 'PUT /api/system/basic/resource/{id}'),
  (1612, 5, 1558, 3, '启用资源',   '', 'system:resource:enable',    '', 30, 1, 0, 1, @now, 1, @now, ''),
  (1613, 5, 1558, 3, '停用资源',   '', 'system:resource:disable',   '', 40, 1, 0, 1, @now, 1, @now, ''),
  (1614, 5, 1558, 3, '关联菜单',   '', 'system:resource:assign-menu','', 50, 1, 0, 1, @now, 1, @now, 'PUT .../resource/{id}/menus'),
  -- 应用管理（父 1554）
  (1620, 5, 1554, 3, '新增应用',   '', 'system:app:create',         '', 10, 1, 0, 1, @now, 1, @now, ''),
  (1621, 5, 1554, 3, '编辑应用',   '', 'system:app:update',         '', 20, 1, 0, 1, @now, 1, @now, ''),
  (1622, 5, 1554, 3, '启停应用',   '', 'system:app:toggle',         '', 30, 1, 0, 1, @now, 1, @now, ''),
  -- 命名空间（父 1561）
  (1630, 5, 1561, 3, '新增命名空间', '', 'system:namespace:create', '', 10, 1, 0, 1, @now, 1, @now, ''),
  (1631, 5, 1561, 3, '编辑命名空间', '', 'system:namespace:update', '', 20, 1, 0, 1, @now, 1, @now, ''),
  (1632, 5, 1561, 3, '启停命名空间', '', 'system:namespace:toggle', '', 30, 1, 0, 1, @now, 1, @now, ''),
  -- 日志管理（父 1562）
  (1640, 5, 1562, 3, '导出日志',   '', 'system:log:export',         '', 10, 1, 0, 1, @now, 1, @now, '接口未实现，权限点预留')
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`), `menu_identification` = VALUES(`menu_identification`),
  `parent_id` = VALUES(`parent_id`), `menu_type` = 3, `sort_num` = VALUES(`sort_num`),
  `menu_status` = 1, `del_flag` = 0, `update_time` = @now;


-- ================================================================
-- 4. URL 资源（basic_resource）
--    现有 id=9（应用查询）、id=10（用户查询）就地升级为标准资源，避免重复行。
--    子路径（/{id}/roles 等）用 suffix 匹配，避免与父路径的 prefix 规则重复。
-- ================================================================
UPDATE `basic_resource`
   SET `name` = '应用列表/详情', `path` = '/api/system/basic/app',
       `http_method` = 'GET', `match_type` = 'prefix',
       `app_code` = 'dashboard', `status` = 1, `updated_at` = @now
 WHERE `id` = 9;

UPDATE `basic_resource`
   SET `name` = '用户列表/详情', `path` = '/api/system/basic/user',
       `http_method` = 'GET', `match_type` = 'prefix',
       `app_code` = 'dashboard', `status` = 1, `updated_at` = @now
 WHERE `id` = 10;

INSERT INTO `basic_resource`
  (`id`, `app_id`, `app_code`, `name`, `path`, `http_method`, `match_type`, `description`, `status`, `created_at`, `updated_at`)
VALUES
  -- 认证相关
  (2001, 5, 'dashboard', '账号登录',     '/api/login/account',  'POST',   'exact',  '登录接口，匿名可访问', 1, @now, @now),
  (2002, 5, 'dashboard', '当前用户信息', '/api/currentUser',    'GET',    'exact',  '需登录，返回权限清单', 1, @now, @now),
  (2003, 5, 'dashboard', '退出登录',     '/api/login/outLogin', 'POST',   'exact',  '无状态，服务端不处理', 1, @now, @now),
  -- 用户管理
  (2011, 5, 'dashboard', '新增用户',     '/api/system/basic/user', 'POST',   'exact',  '', 1, @now, @now),
  (2012, 5, 'dashboard', '编辑用户',     '/api/system/basic/user', 'PUT',    'prefix', '含 /{id} 及其子路径', 1, @now, @now),
  (2013, 5, 'dashboard', '删除用户',     '/api/system/basic/user', 'DELETE', 'prefix', '', 1, @now, @now),
  (2014, 5, 'dashboard', '用户分配角色', '/roles',                 'PUT',    'suffix', '/api/system/basic/user/{id}/roles', 1, @now, @now),
  (2015, 5, 'dashboard', '用户分配用户组','/groups',               'PUT',    'suffix', '/api/system/basic/user/{id}/groups', 1, @now, @now),
  -- 角色管理
  (2020, 5, 'dashboard', '角色列表/详情', '/api/system/basic/role', 'GET',  'prefix', '', 1, @now, @now),
  (2021, 5, 'dashboard', '新增角色',     '/api/system/basic/role', 'POST',   'exact',  '', 1, @now, @now),
  (2022, 5, 'dashboard', '编辑角色',     '/api/system/basic/role', 'PUT',    'prefix', '', 1, @now, @now),
  (2023, 5, 'dashboard', '角色分配用户', '/users',                 'PUT',    'suffix', '/api/system/basic/role/{id}/users', 1, @now, @now),
  (2024, 5, 'dashboard', '角色分配菜单', '/menus',                 'PUT',    'suffix', '/api/system/basic/role/{id}/menus', 1, @now, @now),
  (2025, 5, 'dashboard', '角色分配资源', '/resources',             'PUT',    'suffix', '/api/system/basic/role/{id}/resources', 1, @now, @now),
  (2026, 5, 'dashboard', '角色分配用户组','/groups',               'PUT',    'suffix', '/api/system/basic/role/{id}/groups', 1, @now, @now),
  -- 用户组管理
  (2030, 5, 'dashboard', '用户组列表/详情','/api/system/basic/group','GET',  'prefix', '', 1, @now, @now),
  (2031, 5, 'dashboard', '新增用户组',   '/api/system/basic/group', 'POST',   'exact',  '', 1, @now, @now),
  (2032, 5, 'dashboard', '编辑用户组',   '/api/system/basic/group', 'PUT',    'prefix', '', 1, @now, @now),
  (2033, 5, 'dashboard', '删除用户组',   '/api/system/basic/group', 'DELETE', 'prefix', '', 1, @now, @now),
  (2034, 5, 'dashboard', '用户组分配角色','/roles',                 'PUT',    'suffix', '/api/system/basic/group/{id}/roles', 1, @now, @now),
  (2035, 5, 'dashboard', '用户组分配用户','/users',                 'PUT',    'suffix', '/api/system/basic/group/{id}/users', 1, @now, @now),
  (2036, 5, 'dashboard', '用户组层级维护','/sub-groups',            'PUT',    'suffix', '/api/system/basic/group/{id}/sub-groups', 1, @now, @now),
  (2037, 5, 'dashboard', '用户组上级维护','/parent-groups',         'PUT',    'suffix', '/api/system/basic/group/{id}/parent-groups', 1, @now, @now),
  -- 菜单管理
  (2040, 5, 'dashboard', '菜单列表/详情','/api/system/basic/menu',  'GET',    'prefix', '', 1, @now, @now),
  (2041, 5, 'dashboard', '新增菜单',     '/api/system/basic/menu',  'POST',   'exact',  '', 1, @now, @now),
  (2042, 5, 'dashboard', '编辑菜单',     '/api/system/basic/menu',  'PUT',    'prefix', '', 1, @now, @now),
  (2043, 5, 'dashboard', '菜单关联资源', '/resources',              'PUT',    'suffix', '/api/system/basic/menu/{id}/resources', 1, @now, @now),
  -- 资源管理
  (2050, 5, 'dashboard', '资源列表/详情','/api/system/basic/resource','GET',  'prefix', '', 1, @now, @now),
  (2051, 5, 'dashboard', '新增资源',     '/api/system/basic/resource','POST', 'exact',  '', 1, @now, @now),
  (2052, 5, 'dashboard', '编辑资源',     '/api/system/basic/resource','PUT',  'prefix', '', 1, @now, @now),
  (2053, 5, 'dashboard', '资源关联菜单', '/menus',                  'PUT',    'suffix', '/api/system/basic/resource/{id}/menus', 1, @now, @now),
  -- 应用 / 命名空间 / 日志
  (2061, 5, 'dashboard', '应用写操作',   '/api/system/basic/app',  'POST,PUT','prefix', '含启停', 1, @now, @now),
  (2070, 5, 'dashboard', '命名空间列表', '/api/system/basic/namespace','GET', 'prefix', '', 1, @now, @now),
  (2071, 5, 'dashboard', '命名空间写操作','/api/system/basic/namespace','POST,PUT','prefix','含启停', 1, @now, @now),
  (2080, 5, 'dashboard', '操作日志查询', '/api/system/basic/oper-log','GET',  'prefix', '', 1, @now, @now)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `path` = VALUES(`path`), `http_method` = VALUES(`http_method`),
  `match_type` = VALUES(`match_type`), `description` = VALUES(`description`),
  `status` = 1, `app_code` = 'dashboard', `app_id` = 5, `updated_at` = @now;


-- ================================================================
-- 5. 菜单 ↔ 资源 关联（basic_menu_resource）
--    页面挂读接口，按钮挂它对应的写接口，形成 UI 与接口双层一致。
-- ================================================================
INSERT INTO `basic_menu_resource` (`menu_id`, `resource_id`, `deleted`, `create_time`, `update_time`, `op_user_id`)
SELECT v.menu_id, v.resource_id, 0, @now, @now, 1
  FROM (
        -- 页面级读接口
        SELECT 1554 AS menu_id,   9 AS resource_id   -- 应用管理 → 应用列表
        UNION ALL SELECT 1555,   10                  -- 用户管理 → 用户列表
        UNION ALL SELECT 1556, 2020                  -- 角色管理
        UNION ALL SELECT 1559, 2030                  -- 用户组管理
        UNION ALL SELECT 1557, 2040                  -- 菜单管理
        UNION ALL SELECT 1558, 2050                  -- 资源管理
        UNION ALL SELECT 1561, 2070                  -- 命名空间
        UNION ALL SELECT 1562, 2080                  -- 日志管理
        -- 用户管理按钮
        UNION ALL SELECT 1570, 2011
        UNION ALL SELECT 1571, 2012
        UNION ALL SELECT 1572, 2013
        UNION ALL SELECT 1573, 2012
        UNION ALL SELECT 1574, 2012
        UNION ALL SELECT 1575, 2014
        UNION ALL SELECT 1576, 2015
        UNION ALL SELECT 1577, 2012
        -- 角色管理按钮
        UNION ALL SELECT 1580, 2021
        UNION ALL SELECT 1581, 2022
        UNION ALL SELECT 1582, 2022
        UNION ALL SELECT 1583, 2022
        UNION ALL SELECT 1584, 2023
        UNION ALL SELECT 1585, 2024
        UNION ALL SELECT 1586, 2025
        UNION ALL SELECT 1587, 2026
        -- 用户组管理按钮
        UNION ALL SELECT 1590, 2031
        UNION ALL SELECT 1591, 2032
        UNION ALL SELECT 1592, 2033
        UNION ALL SELECT 1593, 2032
        UNION ALL SELECT 1594, 2032
        UNION ALL SELECT 1595, 2035
        UNION ALL SELECT 1596, 2034
        UNION ALL SELECT 1597, 2036
        -- 菜单管理按钮
        UNION ALL SELECT 1600, 2041
        UNION ALL SELECT 1601, 2042
        UNION ALL SELECT 1602, 2042
        UNION ALL SELECT 1603, 2042
        UNION ALL SELECT 1604, 2043
        -- 资源管理按钮
        UNION ALL SELECT 1610, 2051
        UNION ALL SELECT 1611, 2052
        UNION ALL SELECT 1612, 2052
        UNION ALL SELECT 1613, 2052
        UNION ALL SELECT 1614, 2053
        -- 应用 / 命名空间 / 日志
        UNION ALL SELECT 1620, 2061
        UNION ALL SELECT 1621, 2061
        UNION ALL SELECT 1622, 2061
        UNION ALL SELECT 1630, 2071
        UNION ALL SELECT 1631, 2071
        UNION ALL SELECT 1632, 2071
       ) v
 WHERE NOT EXISTS (
        SELECT 1 FROM `basic_menu_resource` mr
         WHERE mr.`menu_id` = v.menu_id AND mr.`resource_id` = v.resource_id);


-- ================================================================
-- 6. 角色授权（basic_role_permission）
--
--    用户反馈「admin 角色没有授权权限资源点」：
--    master=1 的角色虽然代码层直通，但 basic_role_permission 里一条都没有，
--    导致角色管理页的「分配菜单 / 分配资源」面板显示为空、无法维护。
--    这里给 master=1 的角色写入全量授权，让它既能直通、界面上也看得见。
-- ================================================================

-- ---------- 6.1 master=1 角色：全量菜单（目录 + 菜单 + 按钮） ----------
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, m.`id`, 0, 1, 1, @now, @now
  FROM `basic_role` r
  JOIN `basic_menu` m
 WHERE r.`master` = 1 AND r.`del_flag` = 0
   AND m.`app_id` = 5 AND m.`del_flag` = 0 AND m.`menu_status` = 1
   AND NOT EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`ref_id` = m.`id` AND p.`type` = 1);

-- ---------- 6.2 master=1 角色：全量 URL 资源 ----------
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, res.`id`, 0, 2, 1, @now, @now
  FROM `basic_role` r
  JOIN `basic_resource` res
 WHERE r.`master` = 1 AND r.`del_flag` = 0
   AND res.`app_code` = 'dashboard' AND res.`status` = 1
   AND NOT EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`ref_id` = res.`id` AND p.`type` = 2);

-- ---------- 6.3 非超管角色：只读菜单（目录 + 页面，不含按钮） ----------
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, m.`id`, 0, 1, 1, @now, @now
  FROM `basic_role` r
  JOIN `basic_menu` m
 WHERE r.`master` <> 1 AND r.`del_flag` = 0 AND r.`status` = 1
   AND m.`app_id` = 5 AND m.`del_flag` = 0 AND m.`menu_status` = 1
   AND m.`menu_type` IN (1, 2)
   AND m.`menu_identification` LIKE '%:view'
   AND NOT EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`ref_id` = m.`id` AND p.`type` = 1);

-- ---------- 6.4 非超管角色：只读 URL 资源（GET） ----------
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, res.`id`, 0, 2, 1, @now, @now
  FROM `basic_role` r
  JOIN `basic_resource` res
 WHERE r.`master` <> 1 AND r.`del_flag` = 0 AND r.`status` = 1
   AND res.`app_code` = 'dashboard' AND res.`status` = 1
   AND res.`http_method` = 'GET'
   AND NOT EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`ref_id` = res.`id` AND p.`type` = 2);

-- 审计员额外获得日志查看（2080 是 GET，已被 6.4 覆盖；此处补日志导出按钮权限点）
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, m.`id`, 0, 1, 1, @now, @now
  FROM `basic_role` r
  JOIN `basic_menu` m ON m.`id` = 1640
 WHERE r.`master` <> 1 AND r.`code` = 'auditor' AND r.`del_flag` = 0
   AND NOT EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`ref_id` = m.`id` AND p.`type` = 1);


-- ================================================================
-- 7. 账号可用性修正
--    login_error_count 语义是「剩余重试次数」，当前两个账号都是 0
--    （verify-mysql.sql 5.2 判定为「已耗尽却仍启用」），恢复为 5。
-- ================================================================
UPDATE `basic_user`
   SET `login_error_count` = 5
 WHERE `status` = 1 AND (`login_error_count` IS NULL OR `login_error_count` <= 0);


-- ================================================================
-- 8. 脏数据清理（软删，不物理删除）
--    basic_user_role 里存在 user_id = 1559 / 10 的行——那是菜单 ID 被误当
--    用户 ID 写进去了，真实用户只有 750146 / 750147。
-- ================================================================
UPDATE `basic_user_role` ur
   SET ur.`del_flag` = 1, ur.`update_time` = @now
 WHERE ur.`del_flag` = 0
   AND NOT EXISTS (SELECT 1 FROM `basic_user` u WHERE u.`id` = ur.`user_id`);


-- ================================================================
-- 9. 自检（只读，人工核对）
-- ================================================================

-- 9.1 数据规模（期望：菜单 54、按钮 41、资源 34、授权 ≥ 180）
SELECT 'basic_menu(全部)'  AS item, COUNT(*) AS cnt FROM `basic_menu` WHERE `del_flag` = 0
UNION ALL SELECT 'basic_menu(按钮3)', COUNT(*) FROM `basic_menu` WHERE `del_flag` = 0 AND `menu_type` = 3
UNION ALL SELECT 'basic_resource',    COUNT(*) FROM `basic_resource` WHERE `status` = 1
UNION ALL SELECT 'basic_menu_resource', COUNT(*) FROM `basic_menu_resource` WHERE `deleted` = 0
UNION ALL SELECT 'basic_role_permission', COUNT(*) FROM `basic_role_permission` WHERE `del_flag` = 0;

-- 9.2 每个角色的权限点数量（master=1 的 admin/admiin 应各 54 菜单 + 34 资源）
SELECT r.`id`, r.`code`, r.`name`, r.`master`,
       SUM(CASE WHEN p.`type` = 1 THEN 1 ELSE 0 END) AS menu_cnt,
       SUM(CASE WHEN p.`type` = 2 THEN 1 ELSE 0 END) AS resource_cnt
  FROM `basic_role` r
  LEFT JOIN `basic_role_permission` p ON p.`role_id` = r.`id` AND p.`del_flag` = 0
 WHERE r.`del_flag` = 0
 GROUP BY r.`id`, r.`code`, r.`name`, r.`master`
 ORDER BY r.`id`;

-- 9.3 权限码格式不规范（应为空）
SELECT 'bad_identification' AS problem, m.`id`, m.`menu_identification`
  FROM `basic_menu` m
 WHERE m.`del_flag` = 0 AND m.`menu_identification` NOT LIKE '%:%';

-- 9.4 权限码重复（应为空）
SELECT 'dup_identification' AS problem, m.`menu_identification`, COUNT(*) cnt
  FROM `basic_menu` m
 WHERE m.`del_flag` = 0 AND m.`menu_identification` <> ''
 GROUP BY m.`menu_identification`
HAVING COUNT(*) > 1;

-- 9.5 超管最终可获得的 authorities 数量（应 ≥ 50，含全部按钮权限码）
SELECT 'super_admin_authorities' AS item, COUNT(DISTINCT `menu_identification`) AS cnt
  FROM `basic_menu`
 WHERE `del_flag` = 0 AND `menu_status` = 1 AND `menu_identification` <> '';
