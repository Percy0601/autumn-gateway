-- ================================================================
-- Autumn Gateway Dashboard — 网关可视化配置表结构（PostgreSQL 13+）
--
-- 这 5 张 gw_* 表只负责「网关 application.properties 的可视化维护」：
--   页面配置 → 落库 → 后续导出 properties/yaml → 同步到文件 / Consul / Nacos
--
-- 设计取舍（v1 精简版）：
--   1) 不建过滤器/断言字典表：参数表单 schema 放代码侧静态注册表；
--   2) 不建发布记录表：审计快照后面再加；
--   3) 不建独立 profile 表：环境差异在 gw_config_item.profile_code 一列。
--
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，可重复执行。
-- ================================================================


-- ----------------------------------------------------------------
-- 表 1：gw_config_item —— 网关全局配置项（key-value，按环境隔离）
--   对应 application.properties 里除 routes 外的全部内容
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_config_item (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profile_code  VARCHAR(32)  NOT NULL DEFAULT 'dev',
    config_group  VARCHAR(32)  NOT NULL,
    config_key    VARCHAR(128) NOT NULL,
    config_value  TEXT,
    value_type    VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    is_required   SMALLINT     NOT NULL DEFAULT 0,
    is_encrypted  SMALLINT     NOT NULL DEFAULT 0,
    sort_num      INT          NOT NULL DEFAULT 0,
    remark        VARCHAR(512),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gw_item_profile_key UNIQUE (profile_code, config_key)
);
COMMENT ON TABLE  gw_config_item IS '网关全局配置项（key-value，按环境隔离）';
COMMENT ON COLUMN gw_config_item.id IS '主键';
COMMENT ON COLUMN gw_config_item.profile_code IS '环境编码：dev/test/prod';
COMMENT ON COLUMN gw_config_item.config_group IS '分组：SERVER/DATASOURCE/SCG/AUTHN/AUTHZ/LIMIT/CORS/CUSTOM';
COMMENT ON COLUMN gw_config_item.config_key IS '配置 key，如 autumn.authz.jwt.secret';
COMMENT ON COLUMN gw_config_item.config_value IS '配置值；is_encrypted=1 存密文';
COMMENT ON COLUMN gw_config_item.value_type IS 'STRING/INT/LONG/BOOL/DURATION/LIST/JSON/TEXTAREA，决定前端控件';
COMMENT ON COLUMN gw_config_item.is_required IS '1=必填';
COMMENT ON COLUMN gw_config_item.is_encrypted IS '1=敏感值，加密存储+页面掩码';
COMMENT ON COLUMN gw_config_item.sort_num IS '同组排序';
CREATE INDEX IF NOT EXISTS idx_gw_item_group ON gw_config_item (profile_code, config_group, sort_num);


-- ----------------------------------------------------------------
-- 表 2：gw_route —— 路由（每个下游服务一条）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_route (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_code     VARCHAR(64)  NOT NULL,
    route_name     VARCHAR(128) NOT NULL,
    app_code       VARCHAR(64),
    uri            VARCHAR(512) NOT NULL,
    order_num      INT          NOT NULL DEFAULT 0,
    metadata_json  TEXT,
    status         SMALLINT     NOT NULL DEFAULT 1,
    del_flag       SMALLINT     NOT NULL DEFAULT 0,
    remark         VARCHAR(512),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gw_route_code UNIQUE (route_code)
);
COMMENT ON TABLE  gw_route IS '网关路由（每个下游服务一条）';
COMMENT ON COLUMN gw_route.route_code IS 'SCG route id，全局唯一';
COMMENT ON COLUMN gw_route.uri IS 'lb://service 或 http://host:port';
COMMENT ON COLUMN gw_route.order_num IS '匹配顺序，越小越先';
COMMENT ON COLUMN gw_route.del_flag IS '软删 1=已删';
CREATE INDEX IF NOT EXISTS idx_gw_route_app ON gw_route (app_code, status);


-- ----------------------------------------------------------------
-- 表 3：gw_route_predicate —— 路由断言（1 路由 : N，AND 关系）
-- ----------------------------------------------------------------
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
COMMENT ON COLUMN gw_route_predicate.predicate_name IS 'Path/Method/Host/Header/...';
COMMENT ON COLUMN gw_route_predicate.args_json IS '参数 JSON';
CREATE INDEX IF NOT EXISTS idx_gw_pred_route ON gw_route_predicate (route_id, sort_num);


-- ----------------------------------------------------------------
-- 表 4：gw_route_filter —— 路由过滤器（1 路由 : N，sort_num 链顺序）
-- ----------------------------------------------------------------
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
COMMENT ON COLUMN gw_route_filter.filter_name IS 'Authz/StripPrefix/AddRequestHeader/...';
COMMENT ON COLUMN gw_route_filter.sort_num IS '链内顺序，小者先执行';
CREATE INDEX IF NOT EXISTS idx_gw_rf_route ON gw_route_filter (route_id, sort_num);
CREATE INDEX IF NOT EXISTS idx_gw_rf_name  ON gw_route_filter (filter_name);


-- ----------------------------------------------------------------
-- 表 5：gw_global_filter —— 全局默认过滤器 default-filters
-- ----------------------------------------------------------------
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
CREATE INDEX IF NOT EXISTS idx_gw_gf_sort ON gw_global_filter (sort_num);


-- 自检
SELECT 'gw_config_item'     AS table_name, COUNT(*) AS rows_cnt FROM gw_config_item
UNION ALL SELECT 'gw_route',            COUNT(*) FROM gw_route
UNION ALL SELECT 'gw_route_predicate',  COUNT(*) FROM gw_route_predicate
UNION ALL SELECT 'gw_route_filter',     COUNT(*) FROM gw_route_filter
UNION ALL SELECT 'gw_global_filter',    COUNT(*) FROM gw_global_filter;
