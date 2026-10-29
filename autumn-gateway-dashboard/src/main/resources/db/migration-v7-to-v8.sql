-- ============================================================
-- 存量库升级脚本：v7 → v8（全局配置模板化 + 分组有序 + globalcors 修复）
-- 适用：autumn-security 库（MySQL）
-- 执行：mysql -uroot -p autumn-security < migration-v7-to-v8.sql
-- 说明：每一步都做了存在性判断，可重复执行。
--
-- 内容：
--   1. gw_tpl_definition 增加 config_group 列（仅 CONFIG 模板使用）
--   2. 修复 globalcors 模板 map_key_validator / validator 双反斜杠
--      （H2/MySQL 混用时种子里 '\\w' 的双反斜杠落到库中后，Java 正则 [\\w] 只匹配
--        反斜杠或字母 w，导致 '/api/**' 等 map 键校验失败；统一改单反斜杠）
--   3. 内置 CONFIG 模板种子（7 组全局 KV 配置组）
--   4. gw_config_item 旧 4.x 前缀迁移为 5.0.3 前缀（discovery/forwarded/metrics 三条）
-- ============================================================

SET @db := DATABASE();

-- ------------------------------------------------------------
-- 1. gw_tpl_definition.config_group 列（列不存在才 ALTER）
-- ------------------------------------------------------------
SET @exists := (SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'gw_tpl_definition' AND COLUMN_NAME = 'config_group');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE `gw_tpl_definition` ADD COLUMN `config_group` VARCHAR(32) NULL COMMENT ''仅 CONFIG 模板：该组配置落 gw_config_item.config_group 的值（如 AUTHN/SCG）'' AFTER `scope`',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 修复 globalcors map_key_validator / validator（单反斜杠）
--    MySQL 字符串 '\w' 为未知转义，MySQL 保留反斜杠，落库即为单反斜杠
-- ------------------------------------------------------------
UPDATE `gw_tpl_field` SET `map_key_validator` = '^/[\w*/{}.()-]*$', `validator` = '^/[\w*/{}.()-]*$'
WHERE `id` = 95;

-- ------------------------------------------------------------
-- 3. CONFIG 模板种子（存在即跳过）
-- ------------------------------------------------------------
INSERT INTO `gw_tpl_definition`
(`id`,`kind`,`name`,`display_name`,`category`,`scope`,`config_group`,`singleton_per_route`,`builtin`,`sort_num`,`status`,`remark`,`created_at`,`updated_at`)
SELECT 301,'CONFIG','server','服务基础(server.*)','服务基础','GLOBAL','SERVER',0,1,1,1,'server.port 等服务端口配置',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_definition` WHERE `id` = 301);
INSERT INTO `gw_tpl_definition`
(`id`,`kind`,`name`,`display_name`,`category`,`scope`,`config_group`,`singleton_per_route`,`builtin`,`sort_num`,`status`,`remark`,`created_at`,`updated_at`)
SELECT 302,'CONFIG','spring.application','应用名(spring.application.*)','服务基础','GLOBAL','SERVER',0,1,2,1,'spring.application.name 应用名',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_definition` WHERE `id` = 302);
INSERT INTO `gw_tpl_definition`
(`id`,`kind`,`name`,`display_name`,`category`,`scope`,`config_group`,`singleton_per_route`,`builtin`,`sort_num`,`status`,`remark`,`created_at`,`updated_at`)
SELECT 303,'CONFIG','consul','注册发现(consul.*)','注册发现','GLOBAL','DISCOVERY',0,1,3,1,'Consul 连接配置：host/port/scheme/token/timeout',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_definition` WHERE `id` = 303);
INSERT INTO `gw_tpl_definition`
(`id`,`kind`,`name`,`display_name`,`category`,`scope`,`config_group`,`singleton_per_route`,`builtin`,`sort_num`,`status`,`remark`,`created_at`,`updated_at`)
SELECT 304,'CONFIG','autumn.authn','认证(autumn.authn.*)','认证授权','GLOBAL','AUTHN',0,1,4,1,'OAuth2 认证配置：issuer/client 等',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_definition` WHERE `id` = 304);
INSERT INTO `gw_tpl_definition`
(`id`,`kind`,`name`,`display_name`,`category`,`scope`,`config_group`,`singleton_per_route`,`builtin`,`sort_num`,`status`,`remark`,`created_at`,`updated_at`)
SELECT 305,'CONFIG','autumn.authz','鉴权(autumn.authz.*)','认证授权','GLOBAL','AUTHZ',0,1,5,1,'JWT 鉴权配置：签发/校验/登录限制',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_definition` WHERE `id` = 305);
INSERT INTO `gw_tpl_definition`
(`id`,`kind`,`name`,`display_name`,`category`,`scope`,`config_group`,`singleton_per_route`,`builtin`,`sort_num`,`status`,`remark`,`created_at`,`updated_at`)
SELECT 306,'CONFIG','limit.autumn-gateway','限流(limit.autumn-gateway.*)','限流','GLOBAL','LIMIT',0,1,6,1,'内存限流（Bucket4j）参数',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_definition` WHERE `id` = 306);
INSERT INTO `gw_tpl_definition`
(`id`,`kind`,`name`,`display_name`,`category`,`scope`,`config_group`,`singleton_per_route`,`builtin`,`sort_num`,`status`,`remark`,`created_at`,`updated_at`)
SELECT 307,'CONFIG','spring.cloud.gateway.server.webflux','网关引擎(SCG)','网关引擎','GLOBAL','SCG',0,1,7,1,'SCG 5.0.3 运行时：httpclient 连接池/超时、discovery、forwarded、metrics',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_definition` WHERE `id` = 307);

INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 100,301,'port','服务端口',2,1,NULL,NULL,'8080',NULL,0,0,NULL,'port',1,'server.port',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 100);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 101,302,'name','应用名',1,1,NULL,NULL,'autumn-gateway',NULL,0,0,NULL,'name',1,'spring.application.name',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 101);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 102,303,'host','Consul 地址',1,1,NULL,NULL,'localhost',NULL,0,0,NULL,'host',1,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 102);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 103,303,'port','Consul 端口',2,1,NULL,NULL,'8500',NULL,0,0,NULL,'port',2,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 103);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 104,303,'scheme','协议',8,0,'http','["http","https"]','http',NULL,0,0,NULL,'scheme',3,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 104);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 105,303,'token','Token(敏感)',11,0,NULL,NULL,NULL,NULL,1,0,NULL,'token',4,'consul.token，敏感不回显',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 105);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 106,303,'timeout','超时',6,0,NULL,NULL,'3s',NULL,0,0,NULL,'timeout',5,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 106);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 107,304,'enabled','启用认证',5,0,'true',NULL,NULL,NULL,0,0,NULL,'enabled',1,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 107);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 108,304,'issuer-uri','Issuer URI',1,1,NULL,NULL,'http://localhost:9000',NULL,0,0,NULL,'issuer-uri',2,'授权服务器地址',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 108);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 109,304,'client-id','Client ID',1,1,NULL,NULL,'web-client',NULL,0,0,NULL,'client-id',3,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 109);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 110,305,'enabled','启用鉴权',5,0,'true',NULL,NULL,NULL,0,0,NULL,'enabled',1,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 110);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 111,305,'jwt.secret','JWT 密钥(敏感)',11,1,NULL,NULL,NULL,NULL,1,0,NULL,'jwt.secret',2,'签发与校验共用同一密钥，敏感不回显',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 111);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 112,305,'jwt.issuer','JWT Issuer',1,0,NULL,NULL,'autumn-gateway',NULL,0,0,NULL,'jwt.issuer',3,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 112);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 113,305,'jwt.ttl-seconds','Token 有效期(秒)',3,0,'86400',NULL,NULL,NULL,0,0,NULL,'jwt.ttl-seconds',4,'默认 86400=1 天',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 113);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 114,305,'max-login-attempts','最大登录尝试',2,0,'5',NULL,NULL,NULL,0,0,NULL,'max-login-attempts',5,'超过则锁定',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 114);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 115,305,'login-app-code','登录应用编码',1,0,NULL,NULL,'dashboard',NULL,0,0,NULL,'login-app-code',6,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 115);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 116,306,'enabled','启用限流',5,0,'true',NULL,NULL,NULL,0,0,NULL,'enabled',1,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 116);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 117,306,'replenish-rate','令牌补充/秒',2,0,'1',NULL,NULL,NULL,0,0,NULL,'replenish-rate',2,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 117);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 118,306,'burst-capacity','桶容量',2,0,'2',NULL,NULL,NULL,0,0,NULL,'burst-capacity',3,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 118);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 119,306,'time-window-seconds','时间窗口(秒)',3,0,'10',NULL,NULL,NULL,0,0,NULL,'time-window-seconds',4,'Bucket4j 窗口',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 119);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 120,307,'httpclient.connect-timeout','连接超时',6,0,NULL,NULL,'2000ms',NULL,0,0,NULL,'httpclient.connect-timeout',1,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 120);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 121,307,'httpclient.response-timeout','响应超时',6,0,NULL,NULL,'30s',NULL,0,0,NULL,'httpclient.response-timeout',2,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 121);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 122,307,'httpclient.pool.max-connections','连接池上限',2,0,NULL,NULL,'1000',NULL,0,0,NULL,'httpclient.pool.max-connections',3,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 122);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 123,307,'httpclient.pool.max-idle-time','空闲回收',6,0,NULL,NULL,'30s',NULL,0,0,NULL,'httpclient.pool.max-idle-time',4,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 123);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 124,307,'httpclient.pool.acquire-timeout','取连接超时',6,0,NULL,NULL,'45s',NULL,0,0,NULL,'httpclient.pool.acquire-timeout',5,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 124);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 125,307,'discovery.locator.enabled','服务发现自动路由',5,0,'false',NULL,NULL,NULL,0,0,NULL,'discovery.locator.enabled',6,'开启后按注册中心服务名自动建路由',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 125);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 126,307,'forwarded.enabled','Forwarded 头',5,0,'true',NULL,NULL,NULL,0,0,NULL,'forwarded.enabled',7,NULL,NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 126);
INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT 127,307,'metrics.enabled','网关指标',5,0,'true',NULL,NULL,NULL,0,0,NULL,'metrics.enabled',8,'actuator gateway metrics',NOW(),NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 127);

-- ------------------------------------------------------------
-- 4. gw_config_item 旧 4.x 前缀 → 5.0.3 前缀（新 key 不存在时执行）
-- ------------------------------------------------------------
UPDATE `gw_config_item` SET `config_key` = 'spring.cloud.gateway.server.webflux.discovery.locator.enabled'
WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.discovery.locator.enabled'
  AND NOT EXISTS (SELECT 1 FROM `gw_config_item` WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.server.webflux.discovery.locator.enabled');
UPDATE `gw_config_item` SET `config_key` = 'spring.cloud.gateway.server.webflux.forwarded.enabled'
WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.forwarded.enabled'
  AND NOT EXISTS (SELECT 1 FROM `gw_config_item` WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.server.webflux.forwarded.enabled');
UPDATE `gw_config_item` SET `config_key` = 'spring.cloud.gateway.server.webflux.metrics.enabled'
WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.metrics.enabled'
  AND NOT EXISTS (SELECT 1 FROM `gw_config_item` WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.server.webflux.metrics.enabled');

-- ------------------------------------------------------------
-- 5. 自检
-- ------------------------------------------------------------
SELECT 'tpl_config_count' AS item, COUNT(*) AS cnt FROM `gw_tpl_definition` WHERE `kind` = 'CONFIG';
SELECT 'map_key_validator' AS item, `id`, `map_key_validator` FROM `gw_tpl_field` WHERE `id` = 95;
SELECT `config_group`, COUNT(*) AS cnt FROM `gw_config_item` WHERE `profile_code` = 'dev' GROUP BY `config_group` ORDER BY `config_group`;
