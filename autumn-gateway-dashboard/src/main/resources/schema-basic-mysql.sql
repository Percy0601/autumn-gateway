-- `autumn-security`.basic_app 定义

CREATE TABLE "basic_app" (
                             "id" bigint NOT NULL AUTO_INCREMENT,
                             "app_code" varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短标识，如 order、crm、admin',
                             "name" varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                             "base_path" varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '该应用 API 统一前缀，如 /api/order',
                             "description" varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                             "status" tinyint DEFAULT '1' COMMENT '1=正常 0=禁用',
                             "created_at" datetime DEFAULT CURRENT_TIMESTAMP,
                             "updated_at" datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             PRIMARY KEY ("id"),
                             UNIQUE KEY "uk_appid" ("app_code"),
                             KEY "idx_status" ("status")
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用（多应用隔离）';


-- `autumn-security`.basic_group 定义

CREATE TABLE "basic_group" (
                               "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                               "name" varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '岗位名称，职位名称，title',
                               "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               "deleted" int NOT NULL DEFAULT '0' COMMENT '是否删除：0-否 1-是',
                               "code" varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '岗位编码',
                               "org_id" bigint NOT NULL DEFAULT '0' COMMENT '部门名称',
                               "job_id" bigint NOT NULL DEFAULT '0' COMMENT '岗位ID',
                               PRIMARY KEY ("id") USING BTREE,
                               KEY "basic_user_group_name_IDX" ("name") USING BTREE,
                               KEY "basic_group_org_id_IDX" ("org_id") USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户岗位/职位/Title';


-- `autumn-security`.basic_menu 定义

CREATE TABLE "basic_menu" (
                              "id" bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
                              "app_id" tinyint NOT NULL DEFAULT '0' COMMENT '应用ID',
                              "parent_id" bigint unsigned NOT NULL DEFAULT '0' COMMENT '父级ID',
                              "menu_type" tinyint NOT NULL DEFAULT '0' COMMENT '权限类型：1-目录 2-菜单 3-按钮',
                              "menu_name" varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '菜单名称',
                              "menu_url" varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '接口url（多个采用英文逗号分隔）',
                              "menu_identification" varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '菜单或按钮标识',
                              "menu_icon" varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '菜单图标',
                              "sort_num" int NOT NULL DEFAULT '0' COMMENT '菜单排序',
                              "menu_status" tinyint NOT NULL DEFAULT '1' COMMENT '菜单状态：1-启用 2-停用',
                              "del_flag" tinyint NOT NULL DEFAULT '0' COMMENT '是否删除：0-否 1-是',
                              "create_user" bigint NOT NULL DEFAULT '0' COMMENT '创建人id',
                              "create_time" datetime NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '创建时间',
                              "update_user" bigint unsigned NOT NULL DEFAULT '0' COMMENT '更新人id',
                              "update_time" datetime NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '更新时间',
                              "remark" varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '备注',
                              PRIMARY KEY ("id")
) ENGINE=InnoDB AUTO_INCREMENT=1549 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单';


-- `autumn-security`.basic_oper_log 定义

CREATE TABLE "basic_oper_log" (
                                  "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                  "app_code" varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '系统标识',
                                  "business_type" varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '业务类型',
                                  "title" varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求标题',
                                  "request_id" varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求ID',
                                  "request_url" varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求URL',
                                  "request_method" varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求方法',
                                  "request_data" varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求数据',
                                  "class_method" varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '执行方法',
                                  "consume_time" int NOT NULL DEFAULT '0' COMMENT '执行时间（毫秒）',
                                  "response_data" varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '响应结果',
                                  "status" tinyint NOT NULL DEFAULT '0' COMMENT '操作状态：0-正常 1-异常',
                                  "error_msg" varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '操作错误消息',
                                  "oper_id" bigint NOT NULL DEFAULT '0' COMMENT '操作人',
                                  "oper_ip" varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '操作人IP',
                                  "op_time" datetime NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '操作时间',
                                  PRIMARY KEY ("id") USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=24600 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志记录';


-- `autumn-security`.basic_resource 定义

CREATE TABLE "basic_resource" (
                                  "id" bigint NOT NULL AUTO_INCREMENT,
                                  "app_id" bigint NOT NULL,
                                  "app_code" varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
                                  "name" varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限名称，如"创建订单"',
                                  "path" varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'URL 模式，如 /api/order/:id；MENU 时为路由路径',
                                  "http_method" varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'ALL' COMMENT 'ALL / GET / POST / PUT / DELETE / PATCH',
                                  "match_type" varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'exact' COMMENT 'exact / prefix / suffix',
                                  "description" varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                  "status" tinyint DEFAULT '1' COMMENT '1=正常 0=禁用',
                                  "created_at" datetime DEFAULT CURRENT_TIMESTAMP,
                                  "updated_at" datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  PRIMARY KEY ("id")
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限原子项（合并了 Resource 的 URL 四元组）';


-- `autumn-security`.basic_role 定义

CREATE TABLE "basic_role" (
                              "id" bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
                              "app_id" tinyint NOT NULL DEFAULT '10' COMMENT '应用ID：1-运营后台 2-POP后台 3-B2B后台 4-互联网医院后台 5-数据中台 6-销售助手 10-数据洞察 100-商家后台',
                              "code" varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '角色编码',
                              "name" varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '角色名',
                              "type" tinyint NOT NULL DEFAULT '0' COMMENT '角色类型：1-系统角色 2-自定义角色',
                              "status" tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-启用 2-停用',
                              "del_flag" tinyint NOT NULL DEFAULT '0' COMMENT '是否删除：0-否 1-是',
                              "create_user" bigint unsigned NOT NULL DEFAULT '0' COMMENT '创建人id',
                              "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              "update_user" bigint unsigned NOT NULL DEFAULT '0' COMMENT '更新人id',
                              "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              "remark" varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '备注',
                              "master" int NOT NULL DEFAULT '2' COMMENT '是否是管理员: 1-管理员；2-普通',
                              PRIMARY KEY ("id") USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=549 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';


-- `autumn-security`.basic_role_group 定义

CREATE TABLE "basic_role_group" (
                                    "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                    "role_id" bigint NOT NULL DEFAULT '0' COMMENT '角色ID',
                                    "group_id" bigint NOT NULL DEFAULT '0' COMMENT '用户组ID',
                                    "deleted" int NOT NULL DEFAULT '0' COMMENT '删除状态：0-否 1-是',
                                    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    "op_user_id" bigint NOT NULL DEFAULT '0' COMMENT '操作人',
                                    PRIMARY KEY ("id") USING BTREE,
                                    KEY "basic_role_group_role_id_IDX" ("role_id") USING BTREE,
                                    KEY "basic_role_group_group_id_IDX" ("group_id") USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8286 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组与角色关联表';


-- `autumn-security`.basic_role_permission 定义

CREATE TABLE "basic_role_permission" (
                                         "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                         "role_id" bigint NOT NULL COMMENT '角色Id',
                                         "ref_id" bigint NOT NULL COMMENT 'data_scope_id/menu_id',
                                         "create_time" timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                         "update_time" timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                         "del_flag" int NOT NULL DEFAULT '0' COMMENT '删除状态: 0:否，1：是',
                                         "op_user_id" bigint NOT NULL DEFAULT '0' COMMENT '操作人Id',
                                         "type" int NOT NULL DEFAULT '1' COMMENT '权限类型：1-菜单权限；2-资源；3-数据权限',
                                         PRIMARY KEY ("id"),
                                         KEY "basic_role_permission_role_IDX" ("role_id","ref_id") USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=68210 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';


-- `autumn-security`.basic_user 定义

CREATE TABLE "basic_user" (
                              "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                              "emp_id" varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'ESB人员工号',
                              "admin" int NOT NULL DEFAULT '0' COMMENT '是否是管理员：1-是；0：否',
                              "latest_role_id" bigint NOT NULL DEFAULT '0' COMMENT '最近登录使用角色Id',
                              "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY ("id") USING BTREE,
                              KEY "basic_user_emp_id_IDX" ("emp_id") USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=750146 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础用户表';


-- `autumn-security`.basic_user_access_role 定义

CREATE TABLE "basic_user_access_role" (
                                          "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                          "user_id" bigint NOT NULL DEFAULT '0' COMMENT '用户Id',
                                          "latest_role_id" bigint NOT NULL DEFAULT '0' COMMENT '最近登录使用角色Id',
                                          "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                          "emp_id" varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'jwt头部32位，用于索引。',
                                          "del_flag" int NOT NULL DEFAULT '0' COMMENT '删除标志：0-否，1-是',
                                          "access_time" bigint NOT NULL DEFAULT '0' COMMENT '登录系统时间，就是生成jwt的create_time',
                                          PRIMARY KEY ("id") USING BTREE,
                                          KEY "basic_user_jwt_role_user_id_IDX" ("user_id") USING BTREE,
                                          KEY "basic_user_jwt_role_jwt_short_IDX" ("emp_id") USING BTREE,
                                          KEY "basic_user_access_role_update_time_IDX" ("update_time") USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1014 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户最后登录角色，解决多端登录';


-- `autumn-security`.basic_user_group 定义

CREATE TABLE "basic_user_group" (
                                    "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                    "deleted" int NOT NULL DEFAULT '0' COMMENT '删除：0-否 1-是',
                                    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    "user_id" bigint NOT NULL DEFAULT '0' COMMENT '用户Id',
                                    "group_id" bigint NOT NULL DEFAULT '0' COMMENT '岗位Id',
                                    PRIMARY KEY ("id") USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=265 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工岗位关联表';


-- `autumn-security`.basic_user_role 定义

CREATE TABLE "basic_user_role" (
                                   "id" bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                   "app_id" tinyint NOT NULL DEFAULT '0' COMMENT '应用ID：1-运营后台 2-POP后台 3-B2B后台 4-互联网医院后台 5-数据中台 6-销售助手 100-商家后台',
                                   "eid" bigint unsigned NOT NULL DEFAULT '0' COMMENT '企业ID',
                                   "user_id" bigint unsigned NOT NULL DEFAULT '0' COMMENT '用户ID',
                                   "role_id" bigint unsigned NOT NULL DEFAULT '0' COMMENT '角色ID',
                                   "del_flag" tinyint NOT NULL DEFAULT '0' COMMENT '是否删除：0-否 1-是',
                                   "create_user" bigint unsigned NOT NULL DEFAULT '0' COMMENT '创建人id',
                                   "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   "update_user" bigint unsigned NOT NULL DEFAULT '0' COMMENT '更新人id',
                                   "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   "remark" varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '备注',
                                   PRIMARY KEY ("id") USING BTREE,
                                   KEY "basic_user_role_user_id_IDX" ("user_id") USING BTREE,
                                   KEY "basic_user_role_role_id_IDX" ("role_id") USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1219286 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础用户关联角色表';