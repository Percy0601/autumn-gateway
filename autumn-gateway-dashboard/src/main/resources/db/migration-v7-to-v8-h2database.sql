-- ============================================================
-- 存量库升级脚本：v7 → v8（全局配置模板化 + 分组有序 + globalcors 修复）—— H2 版
--
-- 对应 MySQL 版：db/migration-v7-to-v8.sql
-- 适用：H2 TCP 实例 autumn_security（MODE=MySQL）
--
-- 内容：
--   1. gw_tpl_definition 增加 config_group 列（仅 CONFIG 模板使用）
--   2. 修复 globalcors 模板 map_key_validator / validator 双反斜杠
--      （H2 不转义反斜杠，种子里 '\\w' 存库即两个反斜杠，导致 '/api/**' 等键匹配失败；
--       改为单反斜杠后 Java 正则 [\w] 正常匹配）
--   3. 内置 CONFIG 模板种子（7 组全局 KV 配置组：server/spring.application/consul/
--      autumn.authn/autumn.authz/limit.autumn-gateway/spring.cloud.gateway.server.webflux）
--   4. gw_config_item 旧 4.x 前缀迁移为 5.0.3 前缀（discovery/forwarded/metrics 三条）
--
-- 执行：
--   java -cp D:\opt\h2\bin\h2-2.5.252.jar org.h2.tools.Shell ^
--        -url "jdbc:h2:tcp://localhost:9092/autumn_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE" ^
--        -user sa -password 123456 -script <本文件>
--
-- 幂等：可重复执行（INSERT 均带 NOT EXISTS / 存在即跳过）。
-- ============================================================

-- ---------- 1. gw_tpl_definition.config_group 列 ----------
ALTER TABLE `gw_tpl_definition` ADD COLUMN IF NOT EXISTS `config_group` VARCHAR(32) NULL COMMENT '仅 CONFIG 模板：该组配置落 gw_config_item.config_group 的值（如 AUTHN/SCG）';

-- ---------- 2. 修复 globalcors map_key_validator / validator ----------
UPDATE `gw_tpl_field` SET `map_key_validator` = '^/[\w*/{}.()-]*$', `validator` = '^/[\w*/{}.()-]*$'
WHERE `id` = 95 AND `map_key_validator` LIKE '%\\w%';

-- ---------- 3. CONFIG 模板种子（幂等） ----------
INSERT INTO `gw_tpl_definition`
(`id`,`kind`,`name`,`display_name`,`category`,`scope`,`config_group`,`singleton_per_route`,`builtin`,`sort_num`,`status`,`remark`,`created_at`,`updated_at`)
SELECT * FROM (
SELECT 301 AS id,'CONFIG' AS kind,'server' AS name,'服务基础(server.*)' AS display_name,'服务基础' AS category,'GLOBAL' AS scope,'SERVER' AS config_group,0 AS singleton_per_route,1 AS builtin,1 AS sort_num,1 AS status,'server.port 等服务端口配置' AS remark, NOW() AS created_at, NOW() AS updated_at
UNION ALL SELECT 302,'CONFIG','spring.application','应用名(spring.application.*)','服务基础','GLOBAL','SERVER',0,1,2,1,'spring.application.name 应用名',NOW(),NOW()
UNION ALL SELECT 303,'CONFIG','consul','注册发现(consul.*)','注册发现','GLOBAL','DISCOVERY',0,1,3,1,'Consul 连接配置：host/port/scheme/token/timeout',NOW(),NOW()
UNION ALL SELECT 304,'CONFIG','autumn.authn','认证(autumn.authn.*)','认证授权','GLOBAL','AUTHN',0,1,4,1,'OAuth2 认证配置：issuer/client 等',NOW(),NOW()
UNION ALL SELECT 305,'CONFIG','autumn.authz','鉴权(autumn.authz.*)','认证授权','GLOBAL','AUTHZ',0,1,5,1,'JWT 鉴权配置：签发/校验/登录限制',NOW(),NOW()
UNION ALL SELECT 306,'CONFIG','limit.autumn-gateway','限流(limit.autumn-gateway.*)','限流','GLOBAL','LIMIT',0,1,6,1,'内存限流（Bucket4j）参数',NOW(),NOW()
UNION ALL SELECT 307,'CONFIG','spring.cloud.gateway.server.webflux','网关引擎(SCG)','网关引擎','GLOBAL','SCG',0,1,7,1,'SCG 5.0.3 运行时：httpclient 连接池/超时、discovery、forwarded、metrics',NOW(),NOW()
) t
WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_definition` WHERE `id` = 301);

INSERT INTO `gw_tpl_field`
(`id`,`tpl_id`,`key_name`,`label`,`value_type_id`,`required`,`default_value`,`options_json`,`placeholder`,`validator`,`sensitive`,`is_map_key`,`map_key_validator`,`export_key`,`sort_num`,`remark`,`created_at`,`updated_at`)
SELECT * FROM (
SELECT 100 AS id,301 AS tpl_id,'port' AS key_name,'服务端口' AS label,2 AS value_type_id,1 AS required,NULL AS default_value,NULL AS options_json,'8080' AS placeholder,NULL AS validator,0 AS sensitive,0 AS is_map_key,NULL AS map_key_validator,'port' AS export_key,1 AS sort_num,'server.port' AS remark,NOW() AS created_at,NOW() AS updated_at
UNION ALL SELECT 101,302,'name','应用名',1,1,NULL,NULL,'autumn-gateway',NULL,0,0,NULL,'name',1,'spring.application.name',NOW(),NOW()
UNION ALL SELECT 102,303,'host','Consul 地址',1,1,NULL,NULL,'localhost',NULL,0,0,NULL,'host',1,NULL,NOW(),NOW()
UNION ALL SELECT 103,303,'port','Consul 端口',2,1,NULL,NULL,'8500',NULL,0,0,NULL,'port',2,NULL,NOW(),NOW()
UNION ALL SELECT 104,303,'scheme','协议',8,0,'http','["http","https"]','http',NULL,0,0,NULL,'scheme',3,NULL,NOW(),NOW()
UNION ALL SELECT 105,303,'token','Token(敏感)',11,0,NULL,NULL,NULL,NULL,1,0,NULL,'token',4,'consul.token，敏感不回显',NOW(),NOW()
UNION ALL SELECT 106,303,'timeout','超时',6,0,NULL,NULL,'3s',NULL,0,0,NULL,'timeout',5,NULL,NOW(),NOW()
UNION ALL SELECT 107,304,'enabled','启用认证',5,0,'true',NULL,NULL,NULL,0,0,NULL,'enabled',1,NULL,NOW(),NOW()
UNION ALL SELECT 108,304,'issuer-uri','Issuer URI',1,1,NULL,NULL,'http://localhost:9000',NULL,0,0,NULL,'issuer-uri',2,'授权服务器地址',NOW(),NOW()
UNION ALL SELECT 109,304,'client-id','Client ID',1,1,NULL,NULL,'web-client',NULL,0,0,NULL,'client-id',3,NULL,NOW(),NOW()
UNION ALL SELECT 110,305,'enabled','启用鉴权',5,0,'true',NULL,NULL,NULL,0,0,NULL,'enabled',1,NULL,NOW(),NOW()
UNION ALL SELECT 111,305,'jwt.secret','JWT 密钥(敏感)',11,1,NULL,NULL,NULL,NULL,1,0,NULL,'jwt.secret',2,'签发与校验共用同一密钥，敏感不回显',NOW(),NOW()
UNION ALL SELECT 112,305,'jwt.issuer','JWT Issuer',1,0,NULL,NULL,'autumn-gateway',NULL,0,0,NULL,'jwt.issuer',3,NULL,NOW(),NOW()
UNION ALL SELECT 113,305,'jwt.ttl-seconds','Token 有效期(秒)',3,0,'86400',NULL,NULL,NULL,0,0,NULL,'jwt.ttl-seconds',4,'默认 86400=1 天',NOW(),NOW()
UNION ALL SELECT 114,305,'max-login-attempts','最大登录尝试',2,0,'5',NULL,NULL,NULL,0,0,NULL,'max-login-attempts',5,'超过则锁定',NOW(),NOW()
UNION ALL SELECT 115,305,'login-app-code','登录应用编码',1,0,NULL,NULL,'dashboard',NULL,0,0,NULL,'login-app-code',6,NULL,NOW(),NOW()
UNION ALL SELECT 116,306,'enabled','启用限流',5,0,'true',NULL,NULL,NULL,0,0,NULL,'enabled',1,NULL,NOW(),NOW()
UNION ALL SELECT 117,306,'replenish-rate','令牌补充/秒',2,0,'1',NULL,NULL,NULL,0,0,NULL,'replenish-rate',2,NULL,NOW(),NOW()
UNION ALL SELECT 118,306,'burst-capacity','桶容量',2,0,'2',NULL,NULL,NULL,0,0,NULL,'burst-capacity',3,NULL,NOW(),NOW()
UNION ALL SELECT 119,306,'time-window-seconds','时间窗口(秒)',3,0,'10',NULL,NULL,NULL,0,0,NULL,'time-window-seconds',4,'Bucket4j 窗口',NOW(),NOW()
UNION ALL SELECT 120,307,'httpclient.connect-timeout','连接超时',6,0,NULL,NULL,'2000ms',NULL,0,0,NULL,'httpclient.connect-timeout',1,NULL,NOW(),NOW()
UNION ALL SELECT 121,307,'httpclient.response-timeout','响应超时',6,0,NULL,NULL,'30s',NULL,0,0,NULL,'httpclient.response-timeout',2,NULL,NOW(),NOW()
UNION ALL SELECT 122,307,'httpclient.pool.max-connections','连接池上限',2,0,NULL,NULL,'1000',NULL,0,0,NULL,'httpclient.pool.max-connections',3,NULL,NOW(),NOW()
UNION ALL SELECT 123,307,'httpclient.pool.max-idle-time','空闲回收',6,0,NULL,NULL,'30s',NULL,0,0,NULL,'httpclient.pool.max-idle-time',4,NULL,NOW(),NOW()
UNION ALL SELECT 124,307,'httpclient.pool.acquire-timeout','取连接超时',6,0,NULL,NULL,'45s',NULL,0,0,NULL,'httpclient.pool.acquire-timeout',5,NULL,NOW(),NOW()
UNION ALL SELECT 125,307,'discovery.locator.enabled','服务发现自动路由',5,0,'false',NULL,NULL,NULL,0,0,NULL,'discovery.locator.enabled',6,'开启后按注册中心服务名自动建路由',NOW(),NOW()
UNION ALL SELECT 126,307,'forwarded.enabled','Forwarded 头',5,0,'true',NULL,NULL,NULL,0,0,NULL,'forwarded.enabled',7,NULL,NOW(),NOW()
UNION ALL SELECT 127,307,'metrics.enabled','网关指标',5,0,'true',NULL,NULL,NULL,0,0,NULL,'metrics.enabled',8,'actuator gateway metrics',NOW(),NOW()
) t
WHERE NOT EXISTS (SELECT 1 FROM `gw_tpl_field` WHERE `id` = 100);

-- ---------- 4. gw_config_item 旧 4.x 前缀 → 5.0.3 前缀（新 key 不存在时执行） ----------
UPDATE `gw_config_item` SET `config_key` = 'spring.cloud.gateway.server.webflux.discovery.locator.enabled'
WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.discovery.locator.enabled'
  AND NOT EXISTS (SELECT 1 FROM `gw_config_item` WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.server.webflux.discovery.locator.enabled');
UPDATE `gw_config_item` SET `config_key` = 'spring.cloud.gateway.server.webflux.forwarded.enabled'
WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.forwarded.enabled'
  AND NOT EXISTS (SELECT 1 FROM `gw_config_item` WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.server.webflux.forwarded.enabled');
UPDATE `gw_config_item` SET `config_key` = 'spring.cloud.gateway.server.webflux.metrics.enabled'
WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.metrics.enabled'
  AND NOT EXISTS (SELECT 1 FROM `gw_config_item` WHERE `profile_code` = 'dev' AND `config_key` = 'spring.cloud.gateway.server.webflux.metrics.enabled');

-- ---------- 5. 自检 ----------
SELECT 'tpl_config_count' AS item, COUNT(*) AS cnt FROM `gw_tpl_definition` WHERE `kind` = 'CONFIG';
SELECT 'map_key_validator' AS item, `id`, `map_key_validator` FROM `gw_tpl_field` WHERE `id` = 95;
SELECT `config_group`, COUNT(*) AS cnt FROM `gw_config_item` WHERE `profile_code` = 'dev' GROUP BY `config_group` ORDER BY `config_group`;
