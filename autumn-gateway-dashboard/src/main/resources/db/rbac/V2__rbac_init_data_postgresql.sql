-- ================================================================
-- Autumn Gateway Dashboard — RBAC 初始化数据（PostgreSQL 12+）
--
-- 本文件由 V2__rbac_init_data_mysql.sql 自动转换生成，两者数据完全一致。
-- 转换要点：
--   ON DUPLICATE KEY UPDATE  ->  ON CONFLICT (...) DO UPDATE SET ... EXCLUDED.col
--   反引号标识符              ->  双引号标识符
-- 修改初始化数据时请以 MySQL 版为准，改完重新执行转换，避免两库漂移。
--
-- 前置：schema-basic-mysql.sql → V1__rbac_schema_mysql.sql
--
-- 幂等性：全部 INSERT ... ON CONFLICT (...) DO UPDATE SET，可重复执行。
--         依赖 V1 脚本补上的唯一键（uk_role_permission / uk_user_role_app /
--         uk_user_group / uk_role_group / uk_menu_resource）。
--
-- ID 段规划（硬编码主键，便于跨库对比与人工排查），
-- 整段 ID 留白 100 个步长，后续新增不用改动本脚本：
--   basic_namespace       : 1
--   basic_app             : 1
--   basic_menu            : 1000 ~ 1399    目录 1100 / 菜单 1200 / 按钮 1300
--   basic_resource        : 2000 ~ 2399
--   basic_role            : 1 ~ 9
--   basic_user            : 1 ~ 9
--   basic_group           : 1 ~ 9
--   basic_role_permission : 自增（靠唯一键幂等）
--   basic_user_role       : 自增（靠唯一键幂等）
--   basic_menu_resource   : 自增（靠唯一键幂等）
--
-- 菜单树覆盖的 7 个功能域（对应需求里的 7 条授权关系）：
--   ┌─ 1101 首页
--   ├─ 1102 系统管理
--   │   ├─ 1201 用户管理        → 按钮：新增/编辑/删除/启停/分配角色/分配用户组
--   │   ├─ 1202 角色管理        → 按钮：新增/编辑/删除/启停/分配用户/分配菜单/分配资源/分配用户组
--   │   ├─ 1203 用户组管理      → 按钮：新增/编辑/删除/启停/分配用户/分配角色/分配上下级组
--   │   ├─ 1204 菜单管理        → 按钮：新增/编辑/删除/启停/关联资源
--   │   ├─ 1205 资源管理        → 按钮：新增/编辑/删除/启停/关联菜单
--   │   ├─ 1206 应用管理        → 按钮：新增/编辑/启停
--   │   ├─ 1207 命名空间管理    → 按钮：新增/编辑/启停
--   │   └─ 1208 操作日志        → 按钮：导出
--   └─ 1103 网关运维（预留给后续网关侧权限）
-- ================================================================


-- ================================================================
-- 0. 会话变量：统一时间戳 & 初始密码哈希
--    初始密码明文见脚本末尾的说明，首次登录后必须修改。
--    哈希由 Spring Security BCryptPasswordEncoder(strength=10) 生成。
-- ================================================================
SET @now = NOW();
SET @pwd_admin = '$2a$10$CHXp6w9c5x48j.G4Jwzc7O/7HNwOxdkkHDaPyBZ9mrHuYGZpx6KYS'; -- Autumn#Admin@2026
SET @pwd_user  = '$2a$10$0HoFfcVIWnvvpttEKt6d.uO.9NZvKANE.YKcFG/PEx.YeN4CVWaSC'; -- Autumn#User@2026


-- ================================================================
-- 1. 命名空间 + 应用
--    整个权限体系的根，角色/菜单/资源都挂在下面。
-- ================================================================
INSERT INTO "basic_namespace" ("id", "code", "name", "description", "status", "created_at", "updated_at")
VALUES (1, 'default', '默认命名空间', '系统内置命名空间，承载网关控制台自身权限', 1, @now, @now)
ON CONFLICT (id) DO UPDATE SET "name" = EXCLUDED."name", "description" = EXCLUDED."description", "status" = 1;

INSERT INTO "basic_app" ("id", "app_code", "name", "base_path", "description", "status", "namespace_id", "namespace_code", "created_at", "updated_at")
VALUES (1, 'dashboard', '网关控制台', '/api/system', 'autumn-gateway-dashboard 控制台自身的权限空间', 1, 1, 'default', @now, @now)
ON CONFLICT (id) DO UPDATE SET "name" = EXCLUDED."name", "base_path" = EXCLUDED."base_path", "status" = 1;


-- ================================================================
-- 2. 菜单树（basic_menu）
--
--    字段语义（与 Dashboard 前端契约一致，勿改）：
--      menu_type = 1  目录（菜单分组，不可点）
--      menu_type = 2  菜单（对应一个前端页面路由）
--      menu_type = 3  按钮（不渲染，仅作为权限点，供 v-auth 指令使用）
--
--      menu_url          前端路由，如 system/user/index（menu_type=3 时为空）
--      menu_identification  权限标识，前端 v-auth 指令与后端 @PreAuthorize 共用
--                           命名规范：模块:子模块:动作，动作取 view/create/update/
--                           delete/enable/disable/assign/export
--      menu_icon         前端图标名（Element Plus / 自定义图标库）
--      menu_status = 1   启用；2 = 停用
--      del_flag    = 0   未删除；1 = 已删除
-- ================================================================

-- ---------- 2.1 顶级目录 ----------
INSERT INTO "basic_menu" ("id", "app_id", "parent_id", "menu_type", "menu_name", "menu_url", "menu_identification", "menu_icon", "sort_num", "menu_status", "del_flag", "create_user", "create_time", "update_user", "update_time", "remark")
VALUES
 (1101, 1, 0, 2, '首页',        'dashboard/index',      'dashboard:home:view',  'home',      10, 1, 0, 1, @now, 1, @now, '工作台首页'),
 (1102, 1, 0, 1, '系统管理',    '',                     'system:root:view',     'setting',   20, 1, 0, 1, @now, 1, @now, '权限体系配置入口'),
 (1103, 1, 0, 1, '网关运维',    '',                     'ops:root:view',        'deployment-unit', 30, 1, 0, 1, @now, 1, @now, '预留给网关侧权限，暂未挂页面')
ON CONFLICT (id) DO UPDATE SET "menu_name" = EXCLUDED."menu_name", "menu_url" = EXCLUDED."menu_url",
                        "menu_identification" = EXCLUDED."menu_identification", "menu_icon" = EXCLUDED."menu_icon",
                        "sort_num" = EXCLUDED."sort_num", "parent_id" = EXCLUDED."parent_id",
                        "menu_type" = EXCLUDED."menu_type", "menu_status" = 1, "del_flag" = 0,
                        "update_time" = @now;

-- ---------- 2.2 二级菜单（系统管理下 8 个页面） ----------
INSERT INTO "basic_menu" ("id", "app_id", "parent_id", "menu_type", "menu_name", "menu_url", "menu_identification", "menu_icon", "sort_num", "menu_status", "del_flag", "create_user", "create_time", "update_user", "update_time", "remark")
VALUES
 (1201, 1, 1102, 2, '用户管理',   'system/user/index',     'system:user:view',     'user',         10, 1, 0, 1, @now, 1, @now, ''),
 (1202, 1, 1102, 2, '角色管理',   'system/role/index',     'system:role:view',     'key',          20, 1, 0, 1, @now, 1, @now, ''),
 (1203, 1, 1102, 2, '用户组管理', 'system/group/index',    'system:group:view',    'group',        30, 1, 0, 1, @now, 1, @now, ''),
 (1204, 1, 1102, 2, '菜单管理',   'system/menu/index',     'system:menu:view',     'menu',         40, 1, 0, 1, @now, 1, @now, ''),
 (1205, 1, 1102, 2, '资源管理',   'system/resource/index', 'system:resource:view', 'file',         50, 1, 0, 1, @now, 1, @now, ''),
 (1206, 1, 1102, 2, '应用管理',   'system/app/index',      'system:app:view',      'appstore',     60, 1, 0, 1, @now, 1, @now, ''),
 (1207, 1, 1102, 2, '命名空间',   'system/namespace/index','system:namespace:view','apartment',    70, 1, 0, 1, @now, 1, @now, ''),
 (1208, 1, 1102, 2, '操作日志',   'system/operLog/index',  'system:log:view',      'file-text',    80, 1, 0, 1, @now, 1, @now, '')
ON CONFLICT (id) DO UPDATE SET "menu_name" = EXCLUDED."menu_name", "menu_url" = EXCLUDED."menu_url",
                        "menu_identification" = EXCLUDED."menu_identification", "menu_icon" = EXCLUDED."menu_icon",
                        "sort_num" = EXCLUDED."sort_num", "parent_id" = EXCLUDED."parent_id",
                        "menu_type" = EXCLUDED."menu_type", "menu_status" = 1, "del_flag" = 0,
                        "update_time" = @now;


-- ---------- 2.3 按钮权限点 ----------
-- 命名公式：<模块>:<资源>:<动作>
-- 与 @PreAuthorize("hasAuthority('system:user:create')") 完全一致，直接复制可用。

-- 2.3.1 用户管理按钮
INSERT INTO "basic_menu" ("id", "app_id", "parent_id", "menu_type", "menu_name", "menu_url", "menu_identification", "menu_icon", "sort_num", "menu_status", "del_flag", "create_user", "create_time", "update_user", "update_time", "remark")
VALUES
 (1301, 1, 1201, 3, '新增用户',     '', 'system:user:create',    '', 10, 1, 0, 1, @now, 1, @now, 'POST /api/system/basic/user'),
 (1302, 1, 1201, 3, '编辑用户',     '', 'system:user:update',    '', 20, 1, 0, 1, @now, 1, @now, 'PUT /api/system/basic/user/{id}'),
 (1303, 1, 1201, 3, '删除用户',     '', 'system:user:delete',    '', 30, 1, 0, 1, @now, 1, @now, 'DELETE /api/system/basic/user/{id}'),
 (1304, 1, 1201, 3, '启用用户',     '', 'system:user:enable',    '', 40, 1, 0, 1, @now, 1, @now, 'PUT .../user/{id}/enable'),
 (1305, 1, 1201, 3, '停用用户',     '', 'system:user:disable',   '', 50, 1, 0, 1, @now, 1, @now, 'PUT .../user/{id}/disable'),
 (1306, 1, 1201, 3, '分配角色',     '', 'system:user:assign-role',  '', 60, 1, 0, 1, @now, 1, @now, 'PUT .../user/{id}/roles'),
 (1307, 1, 1201, 3, '分配用户组',   '', 'system:user:assign-group', '', 70, 1, 0, 1, @now, 1, @now, 'PUT .../user/{id}/groups'),
 (1308, 1, 1201, 3, '重置密码',     '', 'system:user:reset-pwd', '', 80, 1, 0, 1, @now, 1, @now, '预留：暂未实现接口')
ON CONFLICT (id) DO UPDATE SET "menu_name" = EXCLUDED."menu_name", "menu_identification" = EXCLUDED."menu_identification",
                        "parent_id" = EXCLUDED."parent_id", "menu_type" = 3, "sort_num" = EXCLUDED."sort_num",
                        "menu_status" = 1, "del_flag" = 0, "update_time" = @now;

-- 2.3.2 角色管理按钮
INSERT INTO "basic_menu" ("id", "app_id", "parent_id", "menu_type", "menu_name", "menu_url", "menu_identification", "menu_icon", "sort_num", "menu_status", "del_flag", "create_user", "create_time", "update_user", "update_time", "remark")
VALUES
 (1321, 1, 1202, 3, '新增角色',   '', 'system:role:create',        '', 10, 1, 0, 1, @now, 1, @now, ''),
 (1322, 1, 1202, 3, '编辑角色',   '', 'system:role:update',        '', 20, 1, 0, 1, @now, 1, @now, ''),
 (1323, 1, 1202, 3, '停用角色',   '', 'system:role:disable',       '', 30, 1, 0, 1, @now, 1, @now, ''),
 (1324, 1, 1202, 3, '启用角色',   '', 'system:role:enable',        '', 40, 1, 0, 1, @now, 1, @now, ''),
 (1325, 1, 1202, 3, '分配用户',   '', 'system:role:assign-user',   '', 50, 1, 0, 1, @now, 1, @now, '角色管理用户'),
 (1326, 1, 1202, 3, '分配菜单',   '', 'system:role:assign-menu',   '', 60, 1, 0, 1, @now, 1, @now, '角色管理菜单'),
 (1327, 1, 1202, 3, '分配资源',   '', 'system:role:assign-resource','', 70, 1, 0, 1, @now, 1, @now, '角色管理后台资源'),
 (1328, 1, 1202, 3, '分配用户组', '', 'system:role:assign-group',  '', 80, 1, 0, 1, @now, 1, @now, '角色关联用户组')
ON CONFLICT (id) DO UPDATE SET "menu_name" = EXCLUDED."menu_name", "menu_identification" = EXCLUDED."menu_identification",
                        "parent_id" = EXCLUDED."parent_id", "menu_type" = 3, "sort_num" = EXCLUDED."sort_num",
                        "menu_status" = 1, "del_flag" = 0, "update_time" = @now;

-- 2.3.3 用户组管理按钮
INSERT INTO "basic_menu" ("id", "app_id", "parent_id", "menu_type", "menu_name", "menu_url", "menu_identification", "menu_icon", "sort_num", "menu_status", "del_flag", "create_user", "create_time", "update_user", "update_time", "remark")
VALUES
 (1341, 1, 1203, 3, '新增用户组',   '', 'system:group:create',        '', 10, 1, 0, 1, @now, 1, @now, ''),
 (1342, 1, 1203, 3, '编辑用户组',   '', 'system:group:update',        '', 20, 1, 0, 1, @now, 1, @now, ''),
 (1343, 1, 1203, 3, '删除用户组',   '', 'system:group:delete',        '', 30, 1, 0, 1, @now, 1, @now, ''),
 (1344, 1, 1203, 3, '停用用户组',   '', 'system:group:disable',       '', 40, 1, 0, 1, @now, 1, @now, ''),
 (1345, 1, 1203, 3, '启用用户组',   '', 'system:group:enable',        '', 50, 1, 0, 1, @now, 1, @now, ''),
 (1346, 1, 1203, 3, '分配用户',     '', 'system:group:assign-user',   '', 60, 1, 0, 1, @now, 1, @now, '用户组关联用户'),
 (1347, 1, 1203, 3, '分配角色',     '', 'system:group:assign-role',   '', 70, 1, 0, 1, @now, 1, @now, '用户组关联角色'),
 (1348, 1, 1203, 3, '维护层级',     '', 'system:group:assign-sub',    '', 80, 1, 0, 1, @now, 1, @now, '上下级用户组')
ON CONFLICT (id) DO UPDATE SET "menu_name" = EXCLUDED."menu_name", "menu_identification" = EXCLUDED."menu_identification",
                        "parent_id" = EXCLUDED."parent_id", "menu_type" = 3, "sort_num" = EXCLUDED."sort_num",
                        "menu_status" = 1, "del_flag" = 0, "update_time" = @now;

-- 2.3.4 菜单管理按钮
INSERT INTO "basic_menu" ("id", "app_id", "parent_id", "menu_type", "menu_name", "menu_url", "menu_identification", "menu_icon", "sort_num", "menu_status", "del_flag", "create_user", "create_time", "update_user", "update_time", "remark")
VALUES
 (1361, 1, 1204, 3, '新增菜单',   '', 'system:menu:create',       '', 10, 1, 0, 1, @now, 1, @now, ''),
 (1362, 1, 1204, 3, '编辑菜单',   '', 'system:menu:update',       '', 20, 1, 0, 1, @now, 1, @now, ''),
 (1363, 1, 1204, 3, '停用菜单',   '', 'system:menu:disable',      '', 30, 1, 0, 1, @now, 1, @now, ''),
 (1364, 1, 1204, 3, '启用菜单',   '', 'system:menu:enable',       '', 40, 1, 0, 1, @now, 1, @now, ''),
 (1365, 1, 1204, 3, '关联资源',   '', 'system:menu:assign-resource','', 50, 1, 0, 1, @now, 1, @now, '菜单关联后台资源')
ON CONFLICT (id) DO UPDATE SET "menu_name" = EXCLUDED."menu_name", "menu_identification" = EXCLUDED."menu_identification",
                        "parent_id" = EXCLUDED."parent_id", "menu_type" = 3, "sort_num" = EXCLUDED."sort_num",
                        "menu_status" = 1, "del_flag" = 0, "update_time" = @now;

-- 2.3.5 资源管理按钮
INSERT INTO "basic_menu" ("id", "app_id", "parent_id", "menu_type", "menu_name", "menu_url", "menu_identification", "menu_icon", "sort_num", "menu_status", "del_flag", "create_user", "create_time", "update_user", "update_time", "remark")
VALUES
 (1381, 1, 1205, 3, '新增资源',   '', 'system:resource:create',   '', 10, 1, 0, 1, @now, 1, @now, ''),
 (1382, 1, 1205, 3, '编辑资源',   '', 'system:resource:update',   '', 20, 1, 0, 1, @now, 1, @now, ''),
 (1383, 1, 1205, 3, '停用资源',   '', 'system:resource:disable',  '', 30, 1, 0, 1, @now, 1, @now, ''),
 (1384, 1, 1205, 3, '启用资源',   '', 'system:resource:enable',   '', 40, 1, 0, 1, @now, 1, @now, ''),
 (1385, 1, 1205, 3, '关联菜单',   '', 'system:resource:assign-menu','', 50, 1, 0, 1, @now, 1, @now, '资源关联菜单')
ON CONFLICT (id) DO UPDATE SET "menu_name" = EXCLUDED."menu_name", "menu_identification" = EXCLUDED."menu_identification",
                        "parent_id" = EXCLUDED."parent_id", "menu_type" = 3, "sort_num" = EXCLUDED."sort_num",
                        "menu_status" = 1, "del_flag" = 0, "update_time" = @now;

-- 2.3.6 应用 / 命名空间 / 日志按钮
INSERT INTO "basic_menu" ("id", "app_id", "parent_id", "menu_type", "menu_name", "menu_url", "menu_identification", "menu_icon", "sort_num", "menu_status", "del_flag", "create_user", "create_time", "update_user", "update_time", "remark")
VALUES
 (1391, 1, 1206, 3, '新增应用',     '', 'system:app:create',        '', 10, 1, 0, 1, @now, 1, @now, ''),
 (1392, 1, 1206, 3, '编辑应用',     '', 'system:app:update',        '', 20, 1, 0, 1, @now, 1, @now, ''),
 (1393, 1, 1206, 3, '启停应用',     '', 'system:app:toggle',        '', 30, 1, 0, 1, @now, 1, @now, ''),
 (1394, 1, 1207, 3, '新增命名空间', '', 'system:namespace:create',  '', 10, 1, 0, 1, @now, 1, @now, ''),
 (1395, 1, 1207, 3, '编辑命名空间', '', 'system:namespace:update',  '', 20, 1, 0, 1, @now, 1, @now, ''),
 (1396, 1, 1207, 3, '启停命名空间', '', 'system:namespace:toggle',  '', 30, 1, 0, 1, @now, 1, @now, ''),
 (1397, 1, 1208, 3, '导出日志',     '', 'system:log:export',        '', 10, 1, 0, 1, @now, 1, @now, '预留：暂未实现导出接口')
ON CONFLICT (id) DO UPDATE SET "menu_name" = EXCLUDED."menu_name", "menu_identification" = EXCLUDED."menu_identification",
                        "parent_id" = EXCLUDED."parent_id", "menu_type" = 3, "sort_num" = EXCLUDED."sort_num",
                        "menu_status" = 1, "del_flag" = 0, "update_time" = @now;


-- ================================================================
-- 3. API 资源（basic_resource）—— URL 级鉴权的原子项
--
--    与 basic_menu 的分工：
--      basic_menu      管「前端看到什么」（目录/菜单/按钮，决定 UI）
--      basic_resource  管「后端接口能不能调」（path + http_method + match_type）
--    两者通过 basic_menu_resource 关联：给角色授了某个菜单，
--    该菜单挂的资源同时在 URL 层放行——这就是「菜单关联后台资源」的意义。
--
--    match_type 取值：
--      exact  精确匹配
--      prefix 前缀匹配（/api/system/basic/user 覆盖 /api/system/basic/user/{id}）
--      suffix 后缀匹配（按结尾匹配，慎用）
--    http_method：ALL 表示所有方法，多方法用英文逗号分隔，如 GET,POST
-- ================================================================
INSERT INTO "basic_resource" ("id", "app_id", "app_code", "name", "path", "http_method", "match_type", "description", "status", "created_at", "updated_at")
VALUES
 -- 认证白名单类（不参与鉴权，登记在此仅为便于在资源管理页统一查看）
 (2001, 1, 'dashboard', '账号登录',       '/api/login/account',        'POST',          'exact',  '登录接口，匿名可访问', 1, @now, @now),
 (2002, 1, 'dashboard', '当前用户信息',   '/api/currentUser',          'GET',           'exact',  '需登录，返回权限清单', 1, @now, @now),
 (2003, 1, 'dashboard', '退出登录',       '/api/login/outLogin',       'POST',          'exact',  '无状态，服务端不处理', 1, @now, @now),

 -- 用户管理
 (2010, 1, 'dashboard', '用户列表/详情',  '/api/system/basic/user',    'GET',           'prefix', 'GET 列表与 /{id} 详情', 1, @now, @now),
 (2011, 1, 'dashboard', '新增用户',       '/api/system/basic/user',    'POST',          'exact',  '', 1, @now, @now),
 (2012, 1, 'dashboard', '编辑用户',       '/api/system/basic/user',    'PUT',           'prefix', '含 /{id} 及其子路径', 1, @now, @now),
 (2013, 1, 'dashboard', '删除用户',       '/api/system/basic/user',    'DELETE',        'prefix', '', 1, @now, @now),
 (2014, 1, 'dashboard', '用户分配角色',   '/api/system/basic/user',    'PUT',           'prefix', '/{id}/roles', 1, @now, @now),
 (2015, 1, 'dashboard', '用户分配用户组', '/api/system/basic/user',    'PUT',           'prefix', '/{id}/groups', 1, @now, @now),

 -- 角色管理
 (2020, 1, 'dashboard', '角色列表/详情',  '/api/system/basic/role',    'GET',           'prefix', '', 1, @now, @now),
 (2021, 1, 'dashboard', '新增角色',       '/api/system/basic/role',    'POST',          'exact',  '', 1, @now, @now),
 (2022, 1, 'dashboard', '编辑角色',       '/api/system/basic/role',    'PUT',           'prefix', '', 1, @now, @now),
 (2023, 1, 'dashboard', '角色分配用户',   '/api/system/basic/role',    'PUT',           'prefix', '/{id}/users', 1, @now, @now),
 (2024, 1, 'dashboard', '角色分配菜单',   '/api/system/basic/role',    'PUT',           'prefix', '/{id}/menus', 1, @now, @now),
 (2025, 1, 'dashboard', '角色分配资源',   '/api/system/basic/role',    'PUT',           'prefix', '/{id}/resources', 1, @now, @now),
 (2026, 1, 'dashboard', '角色分配用户组', '/api/system/basic/role',    'PUT',           'prefix', '/{id}/groups', 1, @now, @now),

 -- 用户组管理
 (2030, 1, 'dashboard', '用户组列表/详情','/api/system/basic/group',   'GET',           'prefix', '', 1, @now, @now),
 (2031, 1, 'dashboard', '新增用户组',     '/api/system/basic/group',   'POST',          'exact',  '', 1, @now, @now),
 (2032, 1, 'dashboard', '编辑用户组',     '/api/system/basic/group',   'PUT',           'prefix', '', 1, @now, @now),
 (2033, 1, 'dashboard', '删除用户组',     '/api/system/basic/group',   'DELETE',        'prefix', '', 1, @now, @now),
 (2034, 1, 'dashboard', '用户组分配角色', '/api/system/basic/group',   'PUT',           'prefix', '/{id}/roles', 1, @now, @now),
 (2035, 1, 'dashboard', '用户组分配用户', '/api/system/basic/group',   'PUT',           'prefix', '/{id}/users', 1, @now, @now),
 (2036, 1, 'dashboard', '用户组层级维护', '/api/system/basic/group',   'PUT',           'prefix', '/{id}/sub-groups,/parent-groups', 1, @now, @now),

 -- 菜单管理
 (2040, 1, 'dashboard', '菜单列表/详情',  '/api/system/basic/menu',    'GET',           'prefix', '', 1, @now, @now),
 (2041, 1, 'dashboard', '新增菜单',       '/api/system/basic/menu',    'POST',          'exact',  '', 1, @now, @now),
 (2042, 1, 'dashboard', '编辑菜单',       '/api/system/basic/menu',    'PUT',           'prefix', '', 1, @now, @now),
 (2043, 1, 'dashboard', '菜单关联资源',   '/api/system/basic/menu',    'PUT',           'prefix', '/{id}/resources', 1, @now, @now),

 -- 资源管理
 (2050, 1, 'dashboard', '资源列表/详情',  '/api/system/basic/resource','GET',           'prefix', '', 1, @now, @now),
 (2051, 1, 'dashboard', '新增资源',       '/api/system/basic/resource','POST',          'exact',  '', 1, @now, @now),
 (2052, 1, 'dashboard', '编辑资源',       '/api/system/basic/resource','PUT',           'prefix', '', 1, @now, @now),
 (2053, 1, 'dashboard', '资源关联菜单',   '/api/system/basic/resource','PUT',           'prefix', '/{id}/menus', 1, @now, @now),

 -- 应用 / 命名空间 / 日志
 (2060, 1, 'dashboard', '应用列表',       '/api/system/basic/app',     'GET',           'prefix', '', 1, @now, @now),
 (2061, 1, 'dashboard', '应用写操作',     '/api/system/basic/app',     'POST,PUT',      'prefix', '含启停', 1, @now, @now),
 (2070, 1, 'dashboard', '命名空间列表',   '/api/system/basic/namespace','GET',          'prefix', '', 1, @now, @now),
 (2071, 1, 'dashboard', '命名空间写操作', '/api/system/basic/namespace','POST,PUT',     'prefix', '含启停', 1, @now, @now),
 (2080, 1, 'dashboard', '操作日志查询',   '/api/system/basic/oper-log','GET',           'prefix', '', 1, @now, @now)
ON CONFLICT (id) DO UPDATE SET "name" = EXCLUDED."name", "path" = EXCLUDED."path", "http_method" = EXCLUDED."http_method",
                        "match_type" = EXCLUDED."match_type", "description" = EXCLUDED."description",
                        "status" = 1, "app_code" = EXCLUDED."app_code", "app_id" = EXCLUDED."app_id",
                        "updated_at" = @now;


-- ================================================================
-- 4. 菜单 ↔ 资源 关联（basic_menu_resource）
--
--    「菜单关联后台资源」在此落地：给角色授了菜单 1201，
--    URL 层就会放行 /api/system/basic/user/** 的读接口。
--    注意：按钮（menu_type=3）同样可以挂资源，实现按钮级 URL 控制，
--    这里为每个按钮挂上它对应的写接口，形成 UI + 接口双层一致。
-- ================================================================
INSERT INTO "basic_menu_resource" ("menu_id", "resource_id", "deleted", "create_time", "update_time", "op_user_id")
VALUES
 -- 用户管理：页面 + 按钮
 (1201, 2010, 0, @now, @now, 1),
 (1301, 2011, 0, @now, @now, 1),
 (1302, 2012, 0, @now, @now, 1),
 (1303, 2013, 0, @now, @now, 1),
 (1304, 2012, 0, @now, @now, 1),
 (1305, 2012, 0, @now, @now, 1),
 (1306, 2014, 0, @now, @now, 1),
 (1307, 2015, 0, @now, @now, 1),
 -- 角色管理：页面 + 按钮
 (1202, 2020, 0, @now, @now, 1),
 (1321, 2021, 0, @now, @now, 1),
 (1322, 2022, 0, @now, @now, 1),
 (1323, 2022, 0, @now, @now, 1),
 (1324, 2022, 0, @now, @now, 1),
 (1325, 2023, 0, @now, @now, 1),
 (1326, 2024, 0, @now, @now, 1),
 (1327, 2025, 0, @now, @now, 1),
 (1328, 2026, 0, @now, @now, 1),
 -- 用户组管理：页面 + 按钮
 (1203, 2030, 0, @now, @now, 1),
 (1341, 2031, 0, @now, @now, 1),
 (1342, 2032, 0, @now, @now, 1),
 (1343, 2033, 0, @now, @now, 1),
 (1344, 2032, 0, @now, @now, 1),
 (1345, 2032, 0, @now, @now, 1),
 (1346, 2035, 0, @now, @now, 1),
 (1347, 2034, 0, @now, @now, 1),
 (1348, 2036, 0, @now, @now, 1),
 -- 菜单管理：页面 + 按钮
 (1204, 2040, 0, @now, @now, 1),
 (1361, 2041, 0, @now, @now, 1),
 (1362, 2042, 0, @now, @now, 1),
 (1363, 2042, 0, @now, @now, 1),
 (1364, 2042, 0, @now, @now, 1),
 (1365, 2043, 0, @now, @now, 1),
 -- 资源管理：页面 + 按钮
 (1205, 2050, 0, @now, @now, 1),
 (1381, 2051, 0, @now, @now, 1),
 (1382, 2052, 0, @now, @now, 1),
 (1383, 2052, 0, @now, @now, 1),
 (1384, 2052, 0, @now, @now, 1),
 (1385, 2053, 0, @now, @now, 1),
 -- 应用 / 命名空间 / 日志
 (1206, 2060, 0, @now, @now, 1),
 (1391, 2061, 0, @now, @now, 1),
 (1392, 2061, 0, @now, @now, 1),
 (1393, 2061, 0, @now, @now, 1),
 (1207, 2070, 0, @now, @now, 1),
 (1394, 2071, 0, @now, @now, 1),
 (1395, 2071, 0, @now, @now, 1),
 (1396, 2071, 0, @now, @now, 1),
 (1208, 2080, 0, @now, @now, 1)
ON CONFLICT (menu_id, resource_id) DO UPDATE SET "deleted" = 0, "update_time" = @now, "op_user_id" = 1;


-- ================================================================
-- 5. 角色（basic_role）
--
--    master = 1 表示「超级管理员」角色：代码层会短路放行所有权限，
--    不依赖 basic_role_permission 的逐条授权，避免新加菜单忘记授权把 admin 锁在门外。
--    master = 2 为普通角色，权限完全由 basic_role_permission 决定。
--
--    角色分级说明：
--      code              master  用途
--      SUPER_ADMIN         1    超管，全权限直通
--      SYS_ADMIN           2    系统管理员，管用户/用户组/角色
--      SECURITY_ADMIN      2    安全管理员，管角色/菜单/资源（与 SYS_ADMIN 职责分离）
--      AUDITOR             2    审计员，只读 + 日志
--      VIEWER              2    只读访客
-- ================================================================
INSERT INTO "basic_role" ("id", "namespace_id", "namespace_code", "code", "name", "type", "status", "del_flag", "create_user", "create_time", "update_user", "update_time", "remark", "master")
VALUES
 (1, 1, 'default', 'SUPER_ADMIN',    '超级管理员',   1, 1, 0, 1, @now, 1, @now, '内置角色，拥有全部权限，不可删除', 1),
 (2, 1, 'default', 'SYS_ADMIN',      '系统管理员',   1, 1, 0, 1, @now, 1, @now, '负责用户、用户组、角色的日常运维', 2),
 (3, 1, 'default', 'SECURITY_ADMIN', '安全管理员',   1, 1, 0, 1, @now, 1, @now, '负责角色授权、菜单与资源配置（职责分离）', 2),
 (4, 1, 'default', 'AUDITOR',        '审计员',       2, 1, 0, 1, @now, 1, @now, '只读全量配置 + 操作日志', 2),
 (5, 1, 'default', 'VIEWER',         '只读访客',     2, 1, 0, 1, @now, 1, @now, '仅可查看，不可变更', 2)
ON CONFLICT (id) DO UPDATE SET "name" = EXCLUDED."name", "remark" = EXCLUDED."remark", "master" = EXCLUDED."master",
                        "status" = 1, "del_flag" = 0, "type" = EXCLUDED."type", "namespace_id" = 1,
                        "namespace_code" = 'default', "update_time" = @now;


-- ================================================================
-- 6. 用户（basic_user）
--
--    初始账号两个：
--      admin  / Autumn#Admin@2026   （admin=1，SUPER_ADMIN）
--      auditor/ Autumn#User@2026    （admin=0，AUDITOR，用于验证「非管理员」视角）
--
--    ⚠️ 这两个密码只用于初始化交付，首次登录后必须立即修改，
--       上线前请确认已改密，并把本脚本的密码哈希替换为团队内部值。
-- ================================================================
INSERT INTO "basic_user" ("id", "uuid", "username", "nickname", "avatar", "email", "phone", "emp_no", "admin", "status", "password", "login_error_count", "latest_role_id", "password_updated_at", "last_login_at", "create_time", "update_time")
VALUES
-- 注意：login_error_count 是「剩余登录重试次数」，初始账号给满 5 次。
 (1, '00000000-0000-0000-0000-000000000001', 'admin',   '超级管理员', '', 'admin@example.com',   '', 'A0001', 1, 1, @pwd_admin, 5, 1, @now, NULL, @now, @now),
 (2, '00000000-0000-0000-0000-000000000002', 'auditor', '审计员',     '', 'auditor@example.com', '', 'A0002', 0, 1, @pwd_user,  5, 4, @now, NULL, @now, @now)
ON CONFLICT (id) DO UPDATE SET "nickname" = EXCLUDED."nickname", "admin" = EXCLUDED."admin", "status" = 1,
                        "update_time" = @now;

-- 注意：不覆盖已存在用户的 password —— 上面 ON CONFLICT (...) DO UPDATE SET 故意不含 password 列，
-- 避免每次重启把用户改过的密码重置回初始值。仅首次 INSERT 时写入初始哈希。
-- 如果确实需要重置，手工执行：
--   UPDATE "basic_user" SET "password" = @pwd_admin WHERE "username" = 'admin';


-- ================================================================
-- 7. 用户组（basic_group）—— 按「岗位」建模
--
--    用户组的价值：给岗位授一次权，组内所有人自动继承，
--    人员变动只改 basic_user_group，不用逐个调角色。
--    组的层级（basic_group_group）支持「上级组的角色向下继承」。
-- ================================================================
INSERT INTO "basic_group" ("id", "namespace_id", "namespace_code", "name", "code", "org_id", "job_id", "status", "deleted", "create_time", "update_time")
VALUES
 (1, 1, 'default', '运维组',   'OPS',   0, 0, 1, 0, @now, @now),
 (2, 1, 'default', '安全组',   'SEC',   0, 0, 1, 0, @now, @now),
 (3, 1, 'default', '审计组',   'AUDIT', 0, 0, 1, 0, @now, @now)
ON CONFLICT (id) DO UPDATE SET "name" = EXCLUDED."name", "code" = EXCLUDED."code", "status" = 1,
                        "deleted" = 0, "update_time" = @now;


-- ================================================================
-- 8. 关系绑定
-- ================================================================

-- ---------- 8.1 用户 → 角色（basic_user_role） ----------
-- admin 不绑角色也能用（admin=1 直通），但为了界面一致性仍绑 SUPER_ADMIN。
-- app_id = 0 表示「跨应用角色」，不绑定具体应用。
INSERT INTO "basic_user_role" ("app_id", "user_id", "role_id", "del_flag", "create_user", "create_time", "update_user", "update_time", "remark")
VALUES
 (0, 1, 1, 0, 1, @now, 1, @now, '超管内置绑定'),
 (0, 2, 4, 0, 1, @now, 1, @now, '审计员账号初始绑定')
ON CONFLICT (user_id, role_id, app_id) DO UPDATE SET "del_flag" = 0, "update_time" = @now;

-- ---------- 8.2 用户 → 用户组（basic_user_group） ----------
INSERT INTO "basic_user_group" ("user_id", "group_id", "deleted", "create_time", "update_time")
VALUES
 (1, 1, 0, @now, @now),
 (2, 3, 0, @now, @now)
ON CONFLICT (user_id, group_id) DO UPDATE SET "deleted" = 0, "update_time" = @now;

-- ---------- 8.3 用户组 → 角色（basic_role_group） ----------
-- 运维组 → 系统管理员；安全组 → 安全管理员；审计组 → 审计员
INSERT INTO "basic_role_group" ("role_id", "group_id", "deleted", "create_time", "update_time", "op_user_id")
VALUES
 (2, 1, 0, @now, @now, 1),
 (3, 2, 0, @now, @now, 1),
 (4, 3, 0, @now, @now, 1)
ON CONFLICT (role_id, group_id) DO UPDATE SET "deleted" = 0, "update_time" = @now, "op_user_id" = 1;

-- ---------- 8.4 用户组层级（basic_group_group） ----------
-- 安全组 与 审计组 挂在 运维组 之下：上级组的角色向下继承。
-- 即运维组内的用户自动获得安全组、审计组的角色（如需严格隔离，
-- 把 basic.rbac.group-inherit-enabled 置为 false，或不在本表建关系）。
INSERT INTO "basic_group_group" ("parent_group_id", "group_id", "deleted", "create_time", "update_time", "op_user_id")
VALUES
 (1, 2, 0, @now, @now, 1),
 (1, 3, 0, @now, @now, 1)
ON CONFLICT (parent_group_id, group_id) DO UPDATE SET "deleted" = 0, "update_time" = @now, "op_user_id" = 1;


-- ================================================================
-- 9. 角色 → 权限（basic_role_permission）
--
--    type: 1 = 菜单权限（ref_id 指向 basic_menu.id）
--          2 = 资源权限（ref_id 指向 basic_resource.id）
--          3 = 数据权限（ref_id 指向数据范围，本期预留）
--
--    授权策略：
--      SUPER_ADMIN    —— 不写记录，代码层 master=1 直通
--      SYS_ADMIN      —— 全部菜单与按钮，不含菜单/资源管理（职责分离）
--      SECURITY_ADMIN —— 全部菜单与按钮，不含用户删除/密码重置
--      AUDITOR        —— 只有 *:view 菜单 + 日志，无任何写按钮
--      VIEWER         —— 只有 *:view 菜单
--
--    下面按「角色 × 菜单」笛卡尔展开，全部幂等（依赖 uk_role_permission）。
-- ================================================================

-- ---------- 9.1 SYS_ADMIN（role_id=2）：除 MENU/RESOURCE 配置外的全部 ----------
INSERT INTO "basic_role_permission" ("role_id", "ref_id", "del_flag", "type", "op_user_id", "create_time", "update_time")
SELECT 2, m.id, 0, 1, 1, @now, @now
  FROM "basic_menu" m
 WHERE m.app_id = 1
   AND m.del_flag = 0
   AND m.id NOT IN (
       -- 菜单管理 / 资源管理的全部权限点（交给安全管理员）
       1204, 1361, 1362, 1363, 1364, 1365,
       1205, 1381, 1382, 1383, 1384, 1385,
       -- 用户删除与重置密码（高危，单独授予）
       1303, 1308
   )
ON CONFLICT (role_id, ref_id, type) DO UPDATE SET "del_flag" = 0, "update_time" = @now;

-- ---------- 9.2 SECURITY_ADMIN（role_id=3）：菜单/资源/角色授配 + 只读用户 ----------
INSERT INTO "basic_role_permission" ("role_id", "ref_id", "del_flag", "type", "op_user_id", "create_time", "update_time")
SELECT 3, m.id, 0, 1, 1, @now, @now
  FROM "basic_menu" m
 WHERE m.app_id = 1
   AND m.del_flag = 0
   AND (
       -- 用户/用户组：只读
       m.id IN (1201, 1203)
       -- 角色、菜单、资源：全功能
       OR m.id IN (1202, 1321, 1322, 1323, 1324, 1325, 1326, 1327, 1328)
       OR m.id IN (1204, 1361, 1362, 1363, 1364, 1365)
       OR m.id IN (1205, 1381, 1382, 1383, 1384, 1385)
       -- 首页、日志
       OR m.id IN (1101, 1208, 1397)
   )
ON CONFLICT (role_id, ref_id, type) DO UPDATE SET "del_flag" = 0, "update_time" = @now;

-- ---------- 9.3 AUDITOR（role_id=4）：只读菜单 + 日志导出 ----------
INSERT INTO "basic_role_permission" ("role_id", "ref_id", "del_flag", "type", "op_user_id", "create_time", "update_time")
SELECT 4, m.id, 0, 1, 1, @now, @now
  FROM "basic_menu" m
 WHERE m.app_id = 1
   AND m.del_flag = 0
   AND m.id IN (1101, 1102, 1201, 1202, 1203, 1204, 1205, 1206, 1207, 1208, 1397)
ON CONFLICT (role_id, ref_id, type) DO UPDATE SET "del_flag" = 0, "update_time" = @now;

-- ---------- 9.4 VIEWER（role_id=5）：纯查看 ----------
INSERT INTO "basic_role_permission" ("role_id", "ref_id", "del_flag", "type", "op_user_id", "create_time", "update_time")
SELECT 5, m.id, 0, 1, 1, @now, @now
  FROM "basic_menu" m
 WHERE m.app_id = 1
   AND m.del_flag = 0
   AND m.id IN (1101, 1102, 1201, 1202, 1203, 1206, 1207, 1208)
ON CONFLICT (role_id, ref_id, type) DO UPDATE SET "del_flag" = 0, "update_time" = @now;

-- ---------- 9.5 资源权限（type=2）----------
-- 说明：URL 级鉴权的权威来源是「角色 → 菜单 → 菜单关联的资源」这条链，
-- 所以本节对 SYS_ADMIN/SECURITY_ADMIN 直接把全部资源授一遍，
-- 保证即便菜单-资源关联表被误改，管理类角色也不会被自己锁死。
-- 业务方若要更细粒度（同一菜单下只放行部分接口），
-- 删掉本节、只保留菜单级授权即可。
INSERT INTO "basic_role_permission" ("role_id", "ref_id", "del_flag", "type", "op_user_id", "create_time", "update_time")
SELECT 2, r.id, 0, 2, 1, @now, @now
  FROM "basic_resource" r
 WHERE r.app_code = 'dashboard' AND r.status = 1
   AND r.id NOT IN (2021, 2022, 2023, 2024, 2025, 2026, 2041, 2042, 2043, 2051, 2052, 2053)
ON CONFLICT (role_id, ref_id, type) DO UPDATE SET "del_flag" = 0, "update_time" = @now;

INSERT INTO "basic_role_permission" ("role_id", "ref_id", "del_flag", "type", "op_user_id", "create_time", "update_time")
SELECT 3, r.id, 0, 2, 1, @now, @now
  FROM "basic_resource" r
 WHERE r.app_code = 'dashboard' AND r.status = 1
   AND r.http_method = 'GET'
ON CONFLICT (role_id, ref_id, type) DO UPDATE SET "del_flag" = 0, "update_time" = @now;

-- 安全管理员额外获得角色/菜单/资源的写权限
INSERT INTO "basic_role_permission" ("role_id", "ref_id", "del_flag", "type", "op_user_id", "create_time", "update_time")
SELECT 3, r.id, 0, 2, 1, @now, @now
  FROM "basic_resource" r
 WHERE r.app_code = 'dashboard' AND r.status = 1
   AND r.id IN (2021, 2022, 2023, 2024, 2025, 2026, 2041, 2042, 2043, 2051, 2052, 2053)
ON CONFLICT (role_id, ref_id, type) DO UPDATE SET "del_flag" = 0, "update_time" = @now;

-- 审计员与访客：只读资源
INSERT INTO "basic_role_permission" ("role_id", "ref_id", "del_flag", "type", "op_user_id", "create_time", "update_time")
SELECT 4, r.id, 0, 2, 1, @now, @now
  FROM "basic_resource" r
 WHERE r.app_code = 'dashboard' AND r.status = 1 AND r.http_method = 'GET'
ON CONFLICT (role_id, ref_id, type) DO UPDATE SET "del_flag" = 0, "update_time" = @now;

INSERT INTO "basic_role_permission" ("role_id", "ref_id", "del_flag", "type", "op_user_id", "create_time", "update_time")
SELECT 5, r.id, 0, 2, 1, @now, @now
  FROM "basic_resource" r
 WHERE r.app_code = 'dashboard' AND r.status = 1 AND r.http_method = 'GET'
   AND r.id NOT IN (2080)
ON CONFLICT (role_id, ref_id, type) DO UPDATE SET "del_flag" = 0, "update_time" = @now;


-- ================================================================
-- 10. 一致性自检（只读，输出可人工核对）
-- ================================================================

-- 10.1 每个角色实际拥有的权限点数量（应：2 约 30+，3 约 20+，4/5 少量）
SELECT r."code" AS role_code, r."name" AS role_name, r."master",
       SUM(CASE WHEN p."type" = 1 THEN 1 ELSE 0 END) AS menu_perm_cnt,
       SUM(CASE WHEN p."type" = 2 THEN 1 ELSE 0 END) AS resource_perm_cnt
  FROM "basic_role" r
  LEFT JOIN "basic_role_permission" p
         ON p."role_id" = r."id" AND p."del_flag" = 0
 WHERE r."del_flag" = 0
 GROUP BY r."id", r."code", r."name", r."master"
 ORDER BY r."id";

-- 10.2 挂在菜单树外、parent_id 指向不存在节点的孤儿菜单（应为空）
SELECT m."id", m."menu_name", m."parent_id"
  FROM "basic_menu" m
  LEFT JOIN "basic_menu" p ON p."id" = m."parent_id"
 WHERE m."parent_id" <> 0 AND p."id" IS NULL;

-- 10.3 被按钮引用但未关联任何菜单的按钮（应为空）
SELECT m."id", m."menu_name"
  FROM "basic_menu" m
  LEFT JOIN "basic_menu" p ON p."id" = m."parent_id"
 WHERE m."menu_type" = 3 AND p."id" IS NULL;

-- 10.4 每个用户经由「直接角色 + 用户组角色（含上级组继承）」的最终角色清单
SELECT u."username",
       GROUP_CONCAT(DISTINCT r2."code" ORDER BY r2."code") AS effective_roles
  FROM "basic_user" u
  LEFT JOIN "basic_user_role" ur ON ur."user_id" = u."id" AND ur."del_flag" = 0
  LEFT JOIN "basic_role" r1       ON r1."id" = ur."role_id" AND r1."del_flag" = 0
  LEFT JOIN "basic_user_group" ug ON ug."user_id" = u."id" AND ug."deleted" = 0
  LEFT JOIN "basic_group_group" gg ON gg."parent_group_id" = ug."group_id" AND gg."deleted" = 0
  LEFT JOIN "basic_role_group" rg ON rg."group_id" IN (ug."group_id", gg."group_id") AND rg."deleted" = 0
  LEFT JOIN "basic_role" r2       ON r2."id" = rg."role_id" AND r2."del_flag" = 0
 GROUP BY u."id", u."username"
 ORDER BY u."id";
