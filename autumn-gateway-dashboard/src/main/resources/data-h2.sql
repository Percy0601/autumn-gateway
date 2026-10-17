-- ============================================================
-- H2 本地开发种子数据（仅用于无外部数据库的本地启动验证）
-- 真实环境请用 mysql / postgresql profile，本文件不参与其初始化。
-- 注意：UserService.authenticate 的密码校验在生产代码中已被注释，
-- 因此 user_auth_account.credential 仅占位即可登录。
-- ============================================================

-- ---- 旧表（登录链路依赖）----
INSERT INTO "application" ("ID", "appid", "NAME", "base_path", "DESCRIPTION", "STATUS", "created_at", "updated_at")
VALUES (1, 'admin', '管理后台', '/api', 'Autumn Gateway 管理后台', 1, NOW(), NOW());

INSERT INTO "user" ("ID", "uuid", "USERNAME", "NICKNAME", "AVATAR", "EMAIL", "PHONE", "emp_no", "STATUS", "password_updated_at", "last_login_at", "created_at", "updated_at")
VALUES (1, 'u-admin-0001', 'admin', '管理员', '', 'admin@autumn.local', '', 'E0001', 1, NULL, NULL, NOW(), NOW());

INSERT INTO "user_auth_account" ("ID", "user_id", "identity_type", "IDENTIFIER", "CREDENTIAL", "ISSUER", "expires_at", "refresh_token", "union_id", "VERIFIED", "credential_updated_at", "created_at", "updated_at")
VALUES (1, 1, 'password', 'admin', 'unused-placeholder', NULL, NULL, NULL, NULL, 1, NULL, NOW(), NOW());

INSERT INTO "user_app" ("ID", "user_id", "app_id", "is_admin", "created_at")
VALUES (1, 1, 1, 1, NOW());

-- ---- 权限/资源演示数据（让 permission 页面非空，便于样式验收）----
INSERT INTO "permission" ("ID", "app_id", "CATEGORY", "CODE", "NAME", "perm_type", "resource_path", "http_method", "match_type", "parent_id", "ICON", "SORT", "HIDDEN", "DESCRIPTION", "STATUS", "created_at", "updated_at")
VALUES (1, 1, 'system', 'app:query', '查询应用', 'API', '/api/system/basic/app', 'GET', 'exact', 0, '', 1, 0, '应用列表查询', 1, NOW(), NOW()),
       (2, 1, 'system', 'user:query', '查询用户', 'API', '/api/system/basic/user', 'GET', 'exact', 0, '', 2, 0, '用户列表查询', 1, NOW(), NOW()),
       (3, 1, 'system', 'role:query', '查询角色', 'API', '/api/system/basic/role', 'GET', 'exact', 0, '', 3, 0, '角色列表查询', 1, NOW(), NOW());

-- ---- basic_* 演示数据（让系统管理页面非空，便于样式验收）----
INSERT INTO basic_app (id, app_code, name, base_path, description, status, created_at, updated_at)
VALUES (1, 'admin', '管理后台', '/api', '平台管理后台', 1, NOW(), NOW());

INSERT INTO basic_user (id, uuid, username, nickname, avatar, email, phone, emp_no, admin, status, latest_role_id, password_updated_at, last_login_at, create_time, update_time)
VALUES (1, 'u-admin-0001', 'admin', '管理员', '', 'admin@autumn.local', '', 'E0001', 1, 1, 0, NULL, NULL, NOW(), NOW());

INSERT INTO basic_role (id, app_id, code, name, type, status, del_flag, create_user, create_time, update_user, update_time, remark, master)
VALUES (1, 1, 'admin', '管理员', 1, 1, 0, 0, NOW(), 0, NOW(), '超级管理员', 1),
       (2, 1, 'auditor', '审计员', 2, 1, 0, 0, NOW(), 0, NOW(), '只读审计', 2);

INSERT INTO basic_menu (id, app_id, parent_id, menu_type, menu_name, menu_url, menu_identification, menu_icon, sort_num, menu_status, del_flag, create_user, create_time, update_user, update_time, remark)
VALUES (1, 1, 0, 1, '系统管理', '/system', 'sys', 'Setting', 0, 1, 0, 0, NOW(), 0, NOW(), '系统管理目录'),
       (2, 1, 1, 2, '应用管理', '/system/app', 'app', 'Appstore', 1, 1, 0, 0, NOW(), 0, NOW(), ''),
       (3, 1, 1, 2, '用户管理', '/system/user', 'user', 'User', 2, 1, 0, 0, NOW(), 0, NOW(), ''),
       (4, 1, 1, 2, '角色管理', '/system/role', 'role', 'Key', 3, 1, 0, 0, NOW(), 0, NOW(), ''),
       (5, 1, 1, 2, '菜单管理', '/system/menu', 'menu', 'Menu', 4, 1, 0, 0, NOW(), 0, NOW(), ''),
       (6, 1, 1, 2, '资源管理', '/system/resource', 'resource', 'Safety', 5, 1, 0, 0, NOW(), 0, NOW(), '');

INSERT INTO basic_resource (id, app_id, app_code, name, path, http_method, match_type, description, status, created_at, updated_at)
VALUES (1, 1, 'admin', '查询应用', '/api/system/basic/app', 'GET', 'exact', '应用列表查询', 1, NOW(), NOW()),
       (2, 1, 'admin', '查询用户', '/api/system/basic/user', 'GET', 'exact', '用户列表查询', 1, NOW(), NOW());

INSERT INTO basic_group (id, name, create_time, update_time, deleted, code, org_id, job_id, status)
VALUES (1, '开发组', NOW(), NOW(), 0, 'dev', 0, 0, 1),
       (2, '运维组', NOW(), NOW(), 0, 'ops', 0, 0, 1);

INSERT INTO basic_user_role (id, app_id, user_id, role_id, del_flag, create_user, create_time, update_user, update_time, remark)
VALUES (1, 1, 1, 1, 0, 0, NOW(), 0, NOW(), '管理员默认角色');

INSERT INTO basic_user_group (id, deleted, create_time, update_time, user_id, group_id)
VALUES (1, 0, NOW(), NOW(), 1, 1);
