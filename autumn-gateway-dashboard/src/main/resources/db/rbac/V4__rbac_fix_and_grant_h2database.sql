-- ================================================================
-- Autumn Gateway Dashboard — RBAC 修复与授权补齐（H2 版）
--
-- 对应 MySQL 版：db/rbac/V4__rbac_fix_and_grant_mysql.sql
-- 适用：H2 TCP 实例 autumn_security（MODE=MySQL，profile=h2database）
--
-- 与 MySQL 版的三处必要改写（其余逻辑一一对应）：
--   1. 【去 ID 硬编码】MySQL 版针对线上库的 1549~1562 菜单 ID 写死 CASE WHEN；
--      H2 库是 init-autumn-security.sql 播的新种子（菜单 ID 1~10），
--      故改为按「旧 menu_identification 值」定位，跨环境可复用。
--   2. 【ON DUPLICATE KEY UPDATE → MERGE INTO ... KEY(...)】H2 不支持
--      VALUES(col) 函数，且 MERGE 才是 H2 的幂等写法。
--   3. 【多表 DELETE JOIN → 子查询】H2 无 MySQL 的 `DELETE t FROM ... JOIN`。
--
-- 幂等：全部 MERGE / NOT EXISTS 判重，可反复执行。
-- 执行后：重启 dashboard 或等 basic.rbac.cache-ttl-seconds（默认 60s）缓存过期。
--
-- 执行：
--   java -cp D:\opt\h2\bin\h2-2.5.252.jar org.h2.tools.Shell ^
--        -url "jdbc:h2:tcp://localhost:9092/autumn_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE" ^
--        -user sa -password 123456 -script <本文件>
-- ================================================================

-- ================================================================
-- 1. 应用：app_code 收敛为 dashboard
--    basic.rbac.app-code=dashboard，而库里 app_code 还是 'admin'，
--    PermissionService#SQL_RESOURCES 里 r.app_code = ? 会永远匹配不上。
-- ================================================================
UPDATE `basic_app`
   SET `app_code` = 'dashboard', `name` = '网关控制台', `updated_at` = CURRENT_TIMESTAMP
 WHERE `app_code` = 'admin';

UPDATE `basic_resource`
   SET `app_code` = 'dashboard'
 WHERE `app_code` = 'admin';


-- ================================================================
-- 2. 菜单权限标识规范化（关键修复）
--    单段 `user` → 三段 `system:user:view`，与 PermCodes.java 完全对齐。
--    按「旧值」定位，不依赖具体 ID；sort_num 一并排好（原全为 0）。
-- ================================================================
UPDATE `basic_menu` SET `menu_identification` = 'system:root:view',      `sort_num` = 20, `update_time` = CURRENT_TIMESTAMP WHERE `menu_identification` = 'sys';
UPDATE `basic_menu` SET `menu_identification` = 'dashboard:home:view',   `sort_num` = 1,  `update_time` = CURRENT_TIMESTAMP WHERE `menu_identification` = 'dashboard';
UPDATE `basic_menu` SET `menu_identification` = 'system:namespace:view', `sort_num` = 70, `update_time` = CURRENT_TIMESTAMP WHERE `menu_identification` = 'namespace';
UPDATE `basic_menu` SET `menu_identification` = 'system:app:view',       `sort_num` = 60, `update_time` = CURRENT_TIMESTAMP WHERE `menu_identification` = 'app';
UPDATE `basic_menu` SET `menu_identification` = 'system:user:view',      `sort_num` = 10, `update_time` = CURRENT_TIMESTAMP WHERE `menu_identification` = 'user';
UPDATE `basic_menu` SET `menu_identification` = 'system:role:view',      `sort_num` = 20, `update_time` = CURRENT_TIMESTAMP WHERE `menu_identification` = 'role';
UPDATE `basic_menu` SET `menu_identification` = 'system:menu:view',      `sort_num` = 40, `update_time` = CURRENT_TIMESTAMP WHERE `menu_identification` = 'menu';
UPDATE `basic_menu` SET `menu_identification` = 'system:resource:view',  `sort_num` = 50, `update_time` = CURRENT_TIMESTAMP WHERE `menu_identification` = 'resource';
UPDATE `basic_menu` SET `menu_identification` = 'system:group:view',     `sort_num` = 30, `update_time` = CURRENT_TIMESTAMP WHERE `menu_identification` = 'group';
UPDATE `basic_menu` SET `menu_identification` = 'system:log:view',       `sort_num` = 80, `update_time` = CURRENT_TIMESTAMP WHERE `menu_identification` = 'operLog';


-- ================================================================
-- 3. 按钮权限点（menu_type=3，41 个）
--    ID 段 1570~1640（H2 种子只用到 1~10，区间空闲）。
--    父级用 identification 现查，避免跨库 ID 不一致。
-- ================================================================
MERGE INTO `basic_menu`
  (`id`, `app_id`, `parent_id`, `menu_type`, `menu_name`, `menu_url`, `menu_identification`,
   `menu_icon`, `sort_num`, `menu_status`, `del_flag`, `create_user`, `create_time`, `update_user`, `update_time`, `remark`)
KEY (`id`)
SELECT v.`id`, a.`id`, p.`id`, 3, v.`menu_name`, '', v.`ident`, '', v.`sort_num`, 1, 0, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, v.`remark`
  FROM (VALUES
        -- 用户管理
        (1570, 'system:user:view', '新增用户',   'system:user:create',        10, 'POST /api/system/basic/user'),
        (1571, 'system:user:view', '编辑用户',   'system:user:update',        20, 'PUT /api/system/basic/user/{id}'),
        (1572, 'system:user:view', '删除用户',   'system:user:delete',        30, 'DELETE /api/system/basic/user/{id}'),
        (1573, 'system:user:view', '启用用户',   'system:user:enable',        40, 'PUT .../user/{id}/enable'),
        (1574, 'system:user:view', '停用用户',   'system:user:disable',       50, 'PUT .../user/{id}/disable'),
        (1575, 'system:user:view', '分配角色',   'system:user:assign-role',   60, 'PUT .../user/{id}/roles'),
        (1576, 'system:user:view', '分配用户组', 'system:user:assign-group',  70, 'PUT .../user/{id}/groups'),
        (1577, 'system:user:view', '重置密码',   'system:user:reset-pwd',     80, 'PUT .../user/{id}/reset-password'),
        -- 角色管理
        (1580, 'system:role:view', '新增角色',   'system:role:create',          10, 'POST /api/system/basic/role'),
        (1581, 'system:role:view', '编辑角色',   'system:role:update',          20, 'PUT /api/system/basic/role/{id}'),
        (1582, 'system:role:view', '启用角色',   'system:role:enable',          30, ''),
        (1583, 'system:role:view', '停用角色',   'system:role:disable',         40, ''),
        (1584, 'system:role:view', '分配用户',   'system:role:assign-user',     50, 'PUT .../role/{id}/users'),
        (1585, 'system:role:view', '分配菜单',   'system:role:assign-menu',     60, 'PUT .../role/{id}/menus'),
        (1586, 'system:role:view', '分配资源',   'system:role:assign-resource', 70, 'PUT .../role/{id}/resources'),
        (1587, 'system:role:view', '分配用户组', 'system:role:assign-group',    80, 'PUT .../role/{id}/groups'),
        -- 用户组管理
        (1590, 'system:group:view', '新增用户组', 'system:group:create',        10, 'POST /api/system/basic/group'),
        (1591, 'system:group:view', '编辑用户组', 'system:group:update',        20, 'PUT /api/system/basic/group/{id}'),
        (1592, 'system:group:view', '删除用户组', 'system:group:delete',        30, 'DELETE /api/system/basic/group/{id}'),
        (1593, 'system:group:view', '启用用户组', 'system:group:enable',        40, ''),
        (1594, 'system:group:view', '停用用户组', 'system:group:disable',       50, ''),
        (1595, 'system:group:view', '分配用户',   'system:group:assign-user',   60, 'PUT .../group/{id}/users'),
        (1596, 'system:group:view', '分配角色',   'system:group:assign-role',   70, 'PUT .../group/{id}/roles'),
        (1597, 'system:group:view', '维护层级',   'system:group:assign-sub',    80, 'PUT .../group/{id}/sub-groups'),
        -- 菜单管理
        (1600, 'system:menu:view', '新增菜单',   'system:menu:create',          10, 'POST /api/system/basic/menu'),
        (1601, 'system:menu:view', '编辑菜单',   'system:menu:update',          20, 'PUT /api/system/basic/menu/{id}'),
        (1602, 'system:menu:view', '启用菜单',   'system:menu:enable',          30, ''),
        (1603, 'system:menu:view', '停用菜单',   'system:menu:disable',         40, ''),
        (1604, 'system:menu:view', '关联资源',   'system:menu:assign-resource', 50, 'PUT .../menu/{id}/resources'),
        -- 资源管理
        (1610, 'system:resource:view', '新增资源', 'system:resource:create',      10, 'POST /api/system/basic/resource'),
        (1611, 'system:resource:view', '编辑资源', 'system:resource:update',      20, 'PUT /api/system/basic/resource/{id}'),
        (1612, 'system:resource:view', '启用资源', 'system:resource:enable',      30, ''),
        (1613, 'system:resource:view', '停用资源', 'system:resource:disable',     40, ''),
        (1614, 'system:resource:view', '关联菜单', 'system:resource:assign-menu', 50, 'PUT .../resource/{id}/menus'),
        -- 应用管理
        (1620, 'system:app:view', '新增应用', 'system:app:create', 10, ''),
        (1621, 'system:app:view', '编辑应用', 'system:app:update', 20, ''),
        (1622, 'system:app:view', '启停应用', 'system:app:toggle', 30, ''),
        -- 命名空间
        (1630, 'system:namespace:view', '新增命名空间', 'system:namespace:create', 10, ''),
        (1631, 'system:namespace:view', '编辑命名空间', 'system:namespace:update', 20, ''),
        (1632, 'system:namespace:view', '启停命名空间', 'system:namespace:toggle', 30, ''),
        -- 日志管理
        (1640, 'system:log:view', '导出日志', 'system:log:export', 10, '接口未实现，权限点预留')
       ) v (`id`, `parent_ident`, `menu_name`, `ident`, `sort_num`, `remark`)
  JOIN `basic_app`  a ON a.`app_code` = 'dashboard'
  JOIN `basic_menu` p ON p.`menu_identification` = v.`parent_ident` AND p.`del_flag` = 0;


-- ================================================================
-- 4. URL 资源（basic_resource）
--    种子里的 id=1（应用查询）、id=2（用户查询）就地升级，避免重复行。
--    子路径（/{id}/roles 等）用 suffix 匹配，避免与父路径 prefix 规则重复。
-- ================================================================
UPDATE `basic_resource`
   SET `name` = '应用列表/详情', `path` = '/api/system/basic/app',
       `http_method` = 'GET', `match_type` = 'prefix',
       `app_code` = 'dashboard', `status` = 1, `updated_at` = CURRENT_TIMESTAMP
 WHERE `id` = 1;

UPDATE `basic_resource`
   SET `name` = '用户列表/详情', `path` = '/api/system/basic/user',
       `http_method` = 'GET', `match_type` = 'prefix',
       `app_code` = 'dashboard', `status` = 1, `updated_at` = CURRENT_TIMESTAMP
 WHERE `id` = 2;

MERGE INTO `basic_resource`
  (`id`, `app_id`, `app_code`, `name`, `path`, `http_method`, `match_type`, `description`, `status`, `created_at`, `updated_at`)
KEY (`id`)
SELECT v.`id`, a.`id`, 'dashboard', v.`name`, v.`path`, v.`method`, v.`match_type`, v.`desc_txt`, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM (VALUES
        -- 认证相关
        (2001, '账号登录',      '/api/login/account',    'POST',    'exact',  '登录接口，匿名可访问'),
        (2002, '当前用户信息',  '/api/currentUser',      'GET',     'exact',  '需登录，返回权限清单'),
        (2003, '退出登录',      '/api/login/outLogin',   'POST',    'exact',  '无状态，服务端不处理'),
        -- 用户管理
        (2011, '新增用户',      '/api/system/basic/user', 'POST',   'exact',  ''),
        (2012, '编辑用户',      '/api/system/basic/user', 'PUT',    'prefix', '含 /{id} 及其子路径'),
        (2013, '删除用户',      '/api/system/basic/user', 'DELETE', 'prefix', ''),
        (2014, '用户分配角色',  '/roles',                 'PUT',    'suffix', '/api/system/basic/user/{id}/roles'),
        (2015, '用户分配用户组','/groups',                'PUT',    'suffix', '/api/system/basic/user/{id}/groups'),
        -- 角色管理
        (2020, '角色列表/详情', '/api/system/basic/role', 'GET',    'prefix', ''),
        (2021, '新增角色',      '/api/system/basic/role', 'POST',   'exact',  ''),
        (2022, '编辑角色',      '/api/system/basic/role', 'PUT',    'prefix', ''),
        (2023, '角色分配用户',  '/users',                 'PUT',    'suffix', '/api/system/basic/role/{id}/users'),
        (2024, '角色分配菜单',  '/menus',                 'PUT',    'suffix', '/api/system/basic/role/{id}/menus'),
        (2025, '角色分配资源',  '/resources',             'PUT',    'suffix', '/api/system/basic/role/{id}/resources'),
        (2026, '角色分配用户组','/groups',                'PUT',    'suffix', '/api/system/basic/role/{id}/groups'),
        -- 用户组管理
        (2030, '用户组列表/详情','/api/system/basic/group','GET',   'prefix', ''),
        (2031, '新增用户组',    '/api/system/basic/group', 'POST',  'exact',  ''),
        (2032, '编辑用户组',    '/api/system/basic/group', 'PUT',   'prefix', ''),
        (2033, '删除用户组',    '/api/system/basic/group', 'DELETE','prefix', ''),
        (2034, '用户组分配角色','/roles',                  'PUT',   'suffix', '/api/system/basic/group/{id}/roles'),
        (2035, '用户组分配用户','/users',                  'PUT',   'suffix', '/api/system/basic/group/{id}/users'),
        (2036, '用户组层级维护','/sub-groups',             'PUT',   'suffix', '/api/system/basic/group/{id}/sub-groups'),
        (2037, '用户组上级维护','/parent-groups',          'PUT',   'suffix', '/api/system/basic/group/{id}/parent-groups'),
        -- 菜单管理
        (2040, '菜单列表/详情','/api/system/basic/menu',   'GET',   'prefix', ''),
        (2041, '新增菜单',    '/api/system/basic/menu',    'POST',  'exact',  ''),
        (2042, '编辑菜单',    '/api/system/basic/menu',    'PUT',   'prefix', ''),
        (2043, '菜单关联资源','/resources',                'PUT',   'suffix', '/api/system/basic/menu/{id}/resources'),
        -- 资源管理
        (2050, '资源列表/详情','/api/system/basic/resource','GET',  'prefix', ''),
        (2051, '新增资源',    '/api/system/basic/resource', 'POST', 'exact',  ''),
        (2052, '编辑资源',    '/api/system/basic/resource', 'PUT',  'prefix', ''),
        (2053, '资源关联菜单','/menus',                     'PUT',  'suffix', '/api/system/basic/resource/{id}/menus'),
        -- 应用 / 命名空间 / 日志
        (2061, '应用写操作',    '/api/system/basic/app',       'POST,PUT','prefix', '含启停'),
        (2070, '命名空间列表',  '/api/system/basic/namespace', 'GET',    'prefix', ''),
        (2071, '命名空间写操作','/api/system/basic/namespace', 'POST,PUT','prefix','含启停'),
        (2080, '操作日志查询',  '/api/system/basic/oper-log',  'GET',    'prefix', '')
       ) v (`id`, `name`, `path`, `method`, `match_type`, `desc_txt`)
  JOIN `basic_app` a ON a.`app_code` = 'dashboard';


-- ================================================================
-- 5. 菜单 ↔ 资源 关联（basic_menu_resource）
--    页面挂读接口，按钮挂它对应的写接口，形成 UI 与接口双层一致。
-- ================================================================
MERGE INTO `basic_menu_resource` (`menu_id`, `resource_id`, `deleted`, `create_time`, `update_time`, `op_user_id`)
KEY (`menu_id`, `resource_id`)
SELECT m.`id`, v.`resource_id`, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1
  FROM (VALUES
        -- 页面级读接口
        ('system:app:view',        1),
        ('system:user:view',       2),
        ('system:role:view',    2020),
        ('system:group:view',   2030),
        ('system:menu:view',    2040),
        ('system:resource:view',2050),
        ('system:namespace:view',2070),
        ('system:log:view',     2080),
        -- 用户管理按钮
        ('system:user:create',      2011),
        ('system:user:update',      2012),
        ('system:user:delete',      2013),
        ('system:user:enable',      2012),
        ('system:user:disable',     2012),
        ('system:user:assign-role', 2014),
        ('system:user:assign-group',2015),
        ('system:user:reset-pwd',   2012),
        -- 角色管理按钮
        ('system:role:create',         2021),
        ('system:role:update',         2022),
        ('system:role:enable',         2022),
        ('system:role:disable',        2022),
        ('system:role:assign-user',    2023),
        ('system:role:assign-menu',    2024),
        ('system:role:assign-resource',2025),
        ('system:role:assign-group',   2026),
        -- 用户组管理按钮
        ('system:group:create',      2031),
        ('system:group:update',      2032),
        ('system:group:delete',      2033),
        ('system:group:enable',      2032),
        ('system:group:disable',     2032),
        ('system:group:assign-user', 2035),
        ('system:group:assign-role', 2034),
        ('system:group:assign-sub',  2036),
        -- 菜单管理按钮
        ('system:menu:create',          2041),
        ('system:menu:update',          2042),
        ('system:menu:enable',          2042),
        ('system:menu:disable',         2042),
        ('system:menu:assign-resource', 2043),
        -- 资源管理按钮
        ('system:resource:create',      2051),
        ('system:resource:update',      2052),
        ('system:resource:enable',      2052),
        ('system:resource:disable',     2052),
        ('system:resource:assign-menu', 2053),
        -- 应用 / 命名空间 / 日志
        ('system:app:create',        2061),
        ('system:app:update',        2061),
        ('system:app:toggle',        2061),
        ('system:namespace:create',  2071),
        ('system:namespace:update',  2071),
        ('system:namespace:toggle',  2071)
       ) v (`ident`, `resource_id`)
  JOIN `basic_menu` m ON m.`menu_identification` = v.`ident` AND m.`del_flag` = 0;


-- ================================================================
-- 6. 角色授权（basic_role_permission）
--    master=1 的角色虽在代码层直通，但库里一条授权都没有，
--    导致「分配菜单 / 分配资源」面板显示为空、无法维护。
-- ================================================================

-- ---------- 6.1 master=1：全量菜单（目录 + 菜单 + 按钮） ----------
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, m.`id`, 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM `basic_role` r
  JOIN `basic_menu` m
 WHERE r.`master` = 1 AND r.`del_flag` = 0
   AND m.`app_id` = (SELECT `id` FROM `basic_app` WHERE `app_code` = 'dashboard')
   AND m.`del_flag` = 0 AND m.`menu_status` = 1
   AND NOT EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`ref_id` = m.`id` AND p.`type` = 1);

-- ---------- 6.2 master=1：全量 URL 资源 ----------
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, res.`id`, 0, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM `basic_role` r
  JOIN `basic_resource` res
 WHERE r.`master` = 1 AND r.`del_flag` = 0
   AND res.`app_code` = 'dashboard' AND res.`status` = 1
   AND NOT EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`ref_id` = res.`id` AND p.`type` = 2);

-- ---------- 6.3 非超管：只读菜单（目录 + 页面，不含按钮） ----------
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, m.`id`, 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM `basic_role` r
  JOIN `basic_menu` m
 WHERE r.`master` <> 1 AND r.`del_flag` = 0 AND r.`status` = 1
   AND m.`app_id` = (SELECT `id` FROM `basic_app` WHERE `app_code` = 'dashboard')
   AND m.`del_flag` = 0 AND m.`menu_status` = 1
   AND m.`menu_type` IN (1, 2)
   AND m.`menu_identification` LIKE '%:view'
   AND NOT EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`ref_id` = m.`id` AND p.`type` = 1);

-- ---------- 6.4 非超管：只读 URL 资源（GET） ----------
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, res.`id`, 0, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM `basic_role` r
  JOIN `basic_resource` res
 WHERE r.`master` <> 1 AND r.`del_flag` = 0 AND r.`status` = 1
   AND res.`app_code` = 'dashboard' AND res.`status` = 1
   AND res.`http_method` = 'GET'
   AND NOT EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`ref_id` = res.`id` AND p.`type` = 2);

-- ---------- 6.5 审计员额外获得「导出日志」按钮 ----------
INSERT INTO `basic_role_permission` (`role_id`, `ref_id`, `del_flag`, `type`, `op_user_id`, `create_time`, `update_time`)
SELECT r.`id`, m.`id`, 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM `basic_role` r
  JOIN `basic_menu` m ON m.`id` = 1640
 WHERE r.`master` <> 1 AND r.`code` = 'auditor' AND r.`del_flag` = 0
   AND NOT EXISTS (
        SELECT 1 FROM `basic_role_permission` p
         WHERE p.`role_id` = r.`id` AND p.`ref_id` = m.`id` AND p.`type` = 1);


-- ================================================================
-- 7. 账号可用性修正
--    login_error_count 语义是「剩余重试次数」，种子值为 0 = 已耗尽。
-- ================================================================
UPDATE `basic_user`
   SET `login_error_count` = 5
 WHERE `status` = 1 AND (`login_error_count` IS NULL OR `login_error_count` <= 0);


-- ================================================================
-- 8. 脏数据清理（软删，不物理删除）
--    basic_user_role 里 user_id 不存在的行（菜单 ID 被误当用户 ID）。
-- ================================================================
UPDATE `basic_user_role`
   SET `del_flag` = 1, `update_time` = CURRENT_TIMESTAMP
 WHERE `del_flag` = 0
   AND NOT EXISTS (SELECT 1 FROM `basic_user` u WHERE u.`id` = `basic_user_role`.`user_id`);
