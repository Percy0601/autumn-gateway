-- ================================================================
-- Autumn Gateway Dashboard — 网关可视化配置表结构（PostgreSQL 13+）
--
-- 表结构、注释、索引与 MySQL 版（gw_schema_mysql.sql）完全对齐，
-- 仅做 PostgreSQL 方言适配：
--   1) 自增主键用 BIGINT GENERATED ALWAYS AS IDENTITY；
--   2) 布尔/状态用小整数 SMALLINT，与 MySQL TINYINT 语义一致；
--   3) 注释用 COMMENT ON TABLE / COMMENT ON COLUMN；
--   4) 索引用 CREATE INDEX IF NOT EXISTS（PostgreSQL 支持幂等建索引）。
--
-- 种子数据（常用过滤器/断言模板、常用配置项）由 Java 侧 GwDataInitializer 幂等播种。
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，可随 spring.sql.init.mode=always 重复执行。
-- ================================================================


-- 表 1：配置中心 / 注册中心连接（Consul / Nacos）
CREATE TABLE IF NOT EXISTS gw_config_center (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    center_code VARCHAR(32)  NOT NULL,
    center_name VARCHAR(64)  NOT NULL,
    center_type VARCHAR(16)  NOT NULL,
    host        VARCHAR(128) NOT NULL,
    port        INT          NOT NULL,
    acl_token   VARCHAR(255),
    namespace   VARCHAR(128),
    status      SMALLINT     NOT NULL DEFAULT 1,
    remark      VARCHAR(512),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gw_center_code UNIQUE (center_code)
);
COMMENT ON TABLE  gw_config_center IS '配置中心/注册中心连接（Consul/Nacos）';
COMMENT ON COLUMN gw_config_center.center_code IS '连接编码，如 consul-local';
COMMENT ON COLUMN gw_config_center.center_name IS '显示名';
COMMENT ON COLUMN gw_config_center.center_type IS '类型：CONSUL / NACOS';
COMMENT ON COLUMN gw_config_center.host        IS '主机地址';
COMMENT ON COLUMN gw_config_center.port        IS '端口，Consul 8500 / Nacos 8848';
COMMENT ON COLUMN gw_config_center.acl_token   IS 'Consul ACL Token（可空）';
COMMENT ON COLUMN gw_config_center.namespace   IS 'Nacos namespace id；Consul 留空';
COMMENT ON COLUMN gw_config_center.status      IS '1=启用 0=停用';


-- 表 2：网关全局配置项（key-value，按环境隔离）
CREATE TABLE IF NOT EXISTS gw_config_item (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profile_code VARCHAR(32)  NOT NULL DEFAULT 'dev',
    config_group VARCHAR(32)  NOT NULL,
    config_key   VARCHAR(128) NOT NULL,
    config_value TEXT,
    value_type   VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    is_required  SMALLINT     NOT NULL DEFAULT 0,
    is_encrypted SMALLINT     NOT NULL DEFAULT 0,
    sort_num     INT          NOT NULL DEFAULT 0,
    remark       VARCHAR(512),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gw_item_profile_key UNIQUE (profile_code, config_key)
);
COMMENT ON TABLE  gw_config_item IS '网关全局配置项（key-value，按环境隔离）';
COMMENT ON COLUMN gw_config_item.profile_code IS '环境编码：dev/test/prod';
COMMENT ON COLUMN gw_config_item.config_group IS '分组：SERVER/SCG/AUTHN/AUTHZ/LIMIT/CORS/CUSTOM';
COMMENT ON COLUMN gw_config_item.config_key   IS '配置 key，如 autumn.authz.jwt.secret';
COMMENT ON COLUMN gw_config_item.config_value IS '配置值；is_encrypted=1 时页面掩码显示';
COMMENT ON COLUMN gw_config_item.value_type   IS 'STRING/INT/LONG/BOOL/DURATION/LIST/JSON/TEXTAREA，决定前端控件';
COMMENT ON COLUMN gw_config_item.is_required  IS '1=必填，空值拦截';
COMMENT ON COLUMN gw_config_item.is_encrypted IS '1=敏感值，页面不回显明文';
COMMENT ON COLUMN gw_config_item.sort_num     IS '同组内排序';
CREATE INDEX IF NOT EXISTS idx_gw_item_group ON gw_config_item (profile_code, config_group, sort_num);


-- 表 3：路由（每个下游服务一条）
CREATE TABLE IF NOT EXISTS gw_route (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_code    VARCHAR(64)  NOT NULL,
    route_name    VARCHAR(128) NOT NULL,
    center_id     BIGINT,
    namespace     VARCHAR(128),
    service_name  VARCHAR(128),
    uri           VARCHAR(512),
    order_num     INT          NOT NULL DEFAULT 0,
    metadata_json TEXT,
    status        SMALLINT     NOT NULL DEFAULT 1,
    del_flag      SMALLINT     NOT NULL DEFAULT 0,
    remark        VARCHAR(512),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gw_route_code UNIQUE (route_code)
);
COMMENT ON TABLE  gw_route IS '网关路由（每个下游服务一条）';
COMMENT ON COLUMN gw_route.route_code   IS 'SCG route id，全局唯一';
COMMENT ON COLUMN gw_route.route_name   IS '显示名';
COMMENT ON COLUMN gw_route.center_id    IS '关联 gw_config_center.id；为空=纯手填直连';
COMMENT ON COLUMN gw_route.namespace    IS '命名空间/分组（Nacos 用）';
COMMENT ON COLUMN gw_route.service_name IS '从注册中心选的服务名；uri 自动拼 lb://service_name';
COMMENT ON COLUMN gw_route.uri          IS 'lb://service_name 或 http://host:port；应用层校验非空';
COMMENT ON COLUMN gw_route.order_num    IS '路由匹配顺序，越小越先';
COMMENT ON COLUMN gw_route.metadata_json IS '路由元数据 Map';
COMMENT ON COLUMN gw_route.del_flag     IS '软删：1=已删';
CREATE INDEX IF NOT EXISTS idx_gw_route_center ON gw_route (center_id, status);


-- 表 4：路由断言（1 路由 : N，AND 关系）
CREATE TABLE IF NOT EXISTS gw_route_predicate (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_id       BIGINT       NOT NULL REFERENCES gw_route(id),
    predicate_name VARCHAR(64)  NOT NULL,
    args_json      TEXT,
    sort_num       INT          NOT NULL DEFAULT 0,
    status         SMALLINT     NOT NULL DEFAULT 1,
    del_flag       SMALLINT     NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  gw_route_predicate IS '路由断言';
COMMENT ON COLUMN gw_route_predicate.predicate_name IS '断言工厂名：Path/Method/Host/Header/...';
COMMENT ON COLUMN gw_route_predicate.args_json      IS '参数键值对 JSON；key 来自模板';
COMMENT ON COLUMN gw_route_predicate.sort_num       IS '页面展示顺序';
CREATE INDEX IF NOT EXISTS idx_gw_pred_route ON gw_route_predicate (route_id, sort_num);


-- 表 5：路由级过滤器（1 路由 : N，sort_num 即链顺序）
CREATE TABLE IF NOT EXISTS gw_route_filter (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_id    BIGINT       NOT NULL REFERENCES gw_route(id),
    filter_name VARCHAR(64)  NOT NULL,
    args_json   TEXT,
    sort_num    INT          NOT NULL DEFAULT 0,
    status      SMALLINT     NOT NULL DEFAULT 1,
    del_flag    SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  gw_route_filter IS '路由级过滤器（带顺序）';
COMMENT ON COLUMN gw_route_filter.filter_name IS '过滤器短名：Authz/StripPrefix/AddRequestHeader/...';
COMMENT ON COLUMN gw_route_filter.sort_num    IS '链内顺序，小者先执行';
CREATE INDEX IF NOT EXISTS idx_gw_rf_route ON gw_route_filter (route_id, sort_num);
CREATE INDEX IF NOT EXISTS idx_gw_rf_name  ON gw_route_filter (filter_name);


-- 表 6：全局默认过滤器 default-filters
CREATE TABLE IF NOT EXISTS gw_global_filter (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    filter_name VARCHAR(64)  NOT NULL,
    args_json   TEXT,
    sort_num    INT          NOT NULL DEFAULT 0,
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    status      SMALLINT     NOT NULL DEFAULT 1,
    del_flag    SMALLINT     NOT NULL DEFAULT 0,
    remark      VARCHAR(512),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  gw_global_filter IS '全局默认过滤器 default-filters（带顺序）';
COMMENT ON COLUMN gw_global_filter.enabled  IS '1=启用 0=停用（保留记录但不导出）';
COMMENT ON COLUMN gw_global_filter.sort_num IS 'default-filters 链内顺序，小者先执行';
CREATE INDEX IF NOT EXISTS idx_gw_gf_sort ON gw_global_filter (sort_num);


-- 表 7：过滤器模板（参数表单定义）
CREATE TABLE IF NOT EXISTS gw_filter_template (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    filter_name         VARCHAR(64)  NOT NULL,
    display_name        VARCHAR(64)  NOT NULL,
    scope               VARCHAR(16)  NOT NULL DEFAULT 'ROUTE',
    singleton_per_route SMALLINT     NOT NULL DEFAULT 0,
    args_schema_json    TEXT,
    status              SMALLINT     NOT NULL DEFAULT 1,
    remark              VARCHAR(512),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gw_filter_tpl UNIQUE (filter_name)
);
COMMENT ON TABLE  gw_filter_template IS '过滤器模板（参数表单定义）';
COMMENT ON COLUMN gw_filter_template.filter_name         IS '过滤器工厂名（唯一）';
COMMENT ON COLUMN gw_filter_template.scope               IS 'ROUTE=仅路由 GLOBAL=仅全局 BOTH=两者皆可';
COMMENT ON COLUMN gw_filter_template.singleton_per_route IS '1=同一路由最多挂一次；0=可挂多次';
COMMENT ON COLUMN gw_filter_template.args_schema_json    IS '参数表单定义数组（JSON），前端据此动态渲染；每项 type=单值(string/int/long/bool/enum/textarea/sensitive) 或 list(多值,配 itemType)';


-- 表 8：断言模板（参数表单定义）
CREATE TABLE IF NOT EXISTS gw_predicate_template (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    predicate_name    VARCHAR(64)  NOT NULL,
    display_name      VARCHAR(64)  NOT NULL,
    args_schema_json  TEXT,
    status            SMALLINT     NOT NULL DEFAULT 1,
    remark            VARCHAR(512),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gw_predicate_tpl UNIQUE (predicate_name)
);
COMMENT ON TABLE  gw_predicate_template IS '断言模板（参数表单定义）';
COMMENT ON COLUMN gw_predicate_template.predicate_name   IS '断言工厂名（唯一）';
COMMENT ON COLUMN gw_predicate_template.args_schema_json IS '参数表单定义数组（JSON），结构同 gw_filter_template（type 支持单值 + list/itemType）';


-- ================================================================
-- v2 模板系统（设计文档：docs/gateway/网关可视化配置-模板系统设计.md）
--   gw_tpl_definition / gw_tpl_field / gw_value_type / gw_value_type_field
--   构成「模板 + 配置项 + 递归类型目录」：支持标量 / list / map / 嵌套 object。
--   gw_config_struct 是 STRUCT 模板（globalcors 等带前缀的全局结构化配置）的实例落点。
--   ※ 种子 SQL（gw_template_seed.sql）为 MySQL/H2 两用版（显式 id），PG 的
--     GENERATED ALWAYS AS IDENTITY 不接受显式 id，执行前把 id 列改
--     GENERATED BY DEFAULT AS IDENTITY，或改用 INSERT ... SELECT 引用。
--   ※ 旧表 gw_filter_template / gw_predicate_template 保留不删（v1 代码仍引用）。
-- ================================================================

-- 表 9：值类型目录（BASIC/LIST/OBJECT，递归）
CREATE TABLE IF NOT EXISTS gw_value_type (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(64)  NOT NULL,
    display_name VARCHAR(64)  NOT NULL,
    kind         VARCHAR(16)  NOT NULL,
    builtin      SMALLINT     NOT NULL DEFAULT 1,
    sort_num     INT          NOT NULL DEFAULT 0,
    status       SMALLINT     NOT NULL DEFAULT 1,
    remark       VARCHAR(512),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gw_vt_name UNIQUE (name)
);
COMMENT ON TABLE  gw_value_type IS '值类型目录（BASIC/LIST/OBJECT，递归）';
COMMENT ON COLUMN gw_value_type.name         IS '类型唯一名，如 STRING / LIST_HTTP_METHOD / OBJECT_CORS';
COMMENT ON COLUMN gw_value_type.kind         IS 'BASIC=标量叶子 LIST=列表 OBJECT=对象';
COMMENT ON COLUMN gw_value_type.builtin      IS '1=内置类型（前端只读）0=用户自定义';


-- 表 10：复合类型结构（LIST 元素 / OBJECT 字段，递归自引用）
CREATE TABLE IF NOT EXISTS gw_value_type_field (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type_id           BIGINT      NOT NULL,
    role              VARCHAR(16) NOT NULL,
    key_name          VARCHAR(64) NOT NULL,
    label             VARCHAR(64),
    value_type_id     BIGINT      NOT NULL,
    required          SMALLINT    NOT NULL DEFAULT 0,
    default_value     VARCHAR(255),
    options_json      TEXT,
    placeholder       VARCHAR(255),
    validator         VARCHAR(255),
    sensitive         SMALLINT    NOT NULL DEFAULT 0,
    is_map_key        SMALLINT    NOT NULL DEFAULT 0,
    map_key_validator VARCHAR(255),
    export_key        VARCHAR(128),
    sort_num          INT         NOT NULL DEFAULT 0,
    remark            VARCHAR(512),
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gw_vtf_type FOREIGN KEY (type_id) REFERENCES gw_value_type (id),
    CONSTRAINT fk_gw_vtf_valtype FOREIGN KEY (value_type_id) REFERENCES gw_value_type (id)
);
CREATE INDEX IF NOT EXISTS idx_gw_vtf_type ON gw_value_type_field (type_id, sort_num);
COMMENT ON TABLE  gw_value_type_field IS '复合类型结构（LIST 元素 / OBJECT 字段，递归）';
COMMENT ON COLUMN gw_value_type_field.role       IS 'ITEM=list元素 FIELD=object字段';
COMMENT ON COLUMN gw_value_type_field.value_type_id IS '该位置的类型（递归引用 gw_value_type.id）';
COMMENT ON COLUMN gw_value_type_field.is_map_key IS '1=该位置实例值为 Map，键由用户输入，值类型=value_type_id';


-- 表 11：模板定义（过滤器/断言/结构化全局，v2）
CREATE TABLE IF NOT EXISTS gw_tpl_definition (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind                VARCHAR(16)  NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    display_name        VARCHAR(64)  NOT NULL,
    category            VARCHAR(32)  NOT NULL DEFAULT '其他',
    scope               VARCHAR(16)  NOT NULL DEFAULT 'ROUTE',
    singleton_per_route SMALLINT     NOT NULL DEFAULT 0,
    builtin             SMALLINT     NOT NULL DEFAULT 1,
    sort_num            INT          NOT NULL DEFAULT 0,
    status              SMALLINT     NOT NULL DEFAULT 1,
    remark              VARCHAR(512),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gw_tpl_kind_name UNIQUE (kind, name)
);
COMMENT ON TABLE  gw_tpl_definition IS '模板定义（过滤器/断言/结构化全局）';
COMMENT ON COLUMN gw_tpl_definition.kind   IS 'FILTER=过滤器 PREDICATE=断言 STRUCT=结构化全局配置';
COMMENT ON COLUMN gw_tpl_definition.scope  IS 'ROUTE=仅路由 GLOBAL=仅全局 BOTH=两者皆可（STRUCT 恒 GLOBAL）';
COMMENT ON COLUMN gw_tpl_definition.builtin IS '1=内置模板（只读/复制）0=用户自定义';


-- 表 12：模板配置项（一个模板 1..N 个配置项）
CREATE TABLE IF NOT EXISTS gw_tpl_field (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tpl_id            BIGINT      NOT NULL,
    key_name          VARCHAR(64) NOT NULL,
    label             VARCHAR(64) NOT NULL,
    value_type_id     BIGINT      NOT NULL,
    required          SMALLINT    NOT NULL DEFAULT 0,
    default_value     VARCHAR(255),
    options_json      TEXT,
    placeholder       VARCHAR(255),
    validator         VARCHAR(255),
    sensitive         SMALLINT    NOT NULL DEFAULT 0,
    is_map_key        SMALLINT    NOT NULL DEFAULT 0,
    map_key_validator VARCHAR(255),
    export_key        VARCHAR(128),
    sort_num          INT         NOT NULL DEFAULT 0,
    remark            VARCHAR(512),
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gw_tpl_field_tpl FOREIGN KEY (tpl_id) REFERENCES gw_tpl_definition (id),
    CONSTRAINT fk_gw_tpl_field_valtype FOREIGN KEY (value_type_id) REFERENCES gw_value_type (id)
);
CREATE INDEX IF NOT EXISTS idx_gw_tpl_field_tpl ON gw_tpl_field (tpl_id, sort_num);
COMMENT ON TABLE  gw_tpl_field IS '模板配置项（标量/list/map/嵌套 object）';
COMMENT ON COLUMN gw_tpl_field.key_name IS '配置项 key：FILTER/PREDICATE 为 args key；STRUCT 为属性段';
COMMENT ON COLUMN gw_tpl_field.is_map_key IS '1=该配置项实例值为 Map：键用户输入，值类型=value_type_id';


-- 表 13：结构化全局配置实例（STRUCT 模板落点）
CREATE TABLE IF NOT EXISTS gw_config_struct (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profile_code  VARCHAR(32)  NOT NULL DEFAULT 'dev',
    struct_name   VARCHAR(64)  NOT NULL,
    instance_name VARCHAR(128),
    value_json    TEXT,
    sort_num      INT          NOT NULL DEFAULT 0,
    status        SMALLINT     NOT NULL DEFAULT 1,
    del_flag      SMALLINT     NOT NULL DEFAULT 0,
    remark        VARCHAR(512),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gw_struct_profile ON gw_config_struct (profile_code, struct_name, status);
COMMENT ON TABLE  gw_config_struct IS '结构化全局配置实例（STRUCT 模板落点）';
COMMENT ON COLUMN gw_config_struct.value_json IS '树形 JSON（含 map 键、嵌套 object/list），按模板校验';
