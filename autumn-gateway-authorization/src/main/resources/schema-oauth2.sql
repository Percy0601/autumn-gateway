-- ============================================================
-- Spring Authorization Server 7.x 持久化表（MySQL 8 / H2 MODE=MySQL 通用）
--
-- 所属库：autumn-security（与 dashboard 共用同一个库）
-- 加载方式：应用启动时由 spring.sql.init 自动执行，见
--           autumn-gateway-authorization/src/main/resources/application.properties:
--             spring.sql.init.mode=always
--             spring.sql.init.schema-locations=classpath:schema-oauth2.sql,classpath:schema-login-security.sql
--           脚本全部 CREATE TABLE IF NOT EXISTS，可重复执行。
--
-- 谁在读写这四张表（列名由 Spring 内部 SQL 硬编码，严禁改名或删列）：
--   1. oauth2_registered_client          → JdbcRegisteredClientRepository
--   2. oauth2_authorization              → JdbcOAuth2AuthorizationService
--   3. oauth2_authorization_consent      → JdbcOAuth2AuthorizationConsentService
--   4. oauth2_jwk                        → 自研 JdbcJwkConfig（本项目扩展，非 Spring 官方表）
--
-- 通用约定：
--   · 带 _value / _metadata / attributes / *_settings 后缀的列，存的都是
--     Jackson 序列化后的 JSON 字节流，不是可读文本——查库看到乱码属正常，请勿手工改写。
--   · 令牌/授权码/JWK 数值列用 BLOB：MySQL 的 BLOB 上限 64KB，若 claims 异常膨胀
--     （如把大权限清单塞进 token）会写入失败，此时应改 LONGBLOB 并同步改实体映射。
--   · 时间列用 TIMESTAMP/DATETIME：TIMESTAMP 受 MySQL 2038 上限约束，
--     令牌 TTL 均远小于该值，故沿用 Spring 官方 DDL 的类型不变。
--   · 除 oauth2_jwk 外，其余三张表的结构与 Spring 官方
--     org/springframework/security/oauth2/server/authorization/*/Jdbc*.sql 一致，
--     升级 Spring Security 大版本时请对照官方脚本核对列清单。
-- ============================================================


-- ------------------------------------------------------------
-- 1. OAuth2 客户端注册表
--    一条记录 = 一个接入方（如网关 web-client）。由管理端/初始化器写入，
--    授权服务器在校验 client_id / client_secret / redirect_uri 时读取。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_registered_client (
  id                            VARCHAR(100) NOT NULL COMMENT '主键：RegisteredClient 内部唯一 ID（UUID 字符串），仅供外键引用，不对外暴露',
  client_id                     VARCHAR(100) NOT NULL COMMENT '客户端标识（对外唯一）：即 OAuth2 协议里的 client_id，如 web-client',
  client_id_issued_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '客户端注册/签发时间',
  client_secret                 VARCHAR(200) DEFAULT NULL COMMENT '客户端密钥：PasswordEncoder 加密后的哈希（BCrypt 60 字符起步，故留 200）；NULL 表示公共客户端（无密钥）',
  client_secret_expires_at      TIMESTAMP DEFAULT NULL COMMENT '密钥过期时间；NULL = 永不过期（当前所有客户端均为 NULL）',
  client_name                   VARCHAR(200) NOT NULL COMMENT '客户端显示名：授权确认页展示给用户看的名称',
  client_authentication_methods VARCHAR(1000) NOT NULL COMMENT '客户端认证方式，逗号分隔：client_secret_basic / client_secret_post / none（公共客户端）',
  authorization_grant_types     VARCHAR(1000) NOT NULL COMMENT '允许的授权模式，逗号分隔：authorization_code / refresh_token / client_credentials / urn:ietf:params:oauth:grant-type:device_code',
  redirect_uris                 VARCHAR(1000) DEFAULT NULL COMMENT '回调地址白名单，逗号分隔；必须精确匹配，否则授权请求被拒（invalid_redirect_uri）',
  post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL COMMENT 'OIDC 登出后可跳转的地址，逗号分隔；未登记则 /connect/logout 不能带 post_logout_redirect_uri',
  scopes                        VARCHAR(1000) NOT NULL COMMENT '允许申请的 scope，逗号分隔：openid / profile / business:read 等',
  client_settings               VARCHAR(2000) NOT NULL COMMENT 'ClientSettings 的 JSON：requireProofKey（强制 PKCE）、requireAuthorizationConsent（是否弹授权确认页）等',
  token_settings                VARCHAR(2000) NOT NULL COMMENT 'TokenSettings 的 JSON：各类令牌 TTL、reuseRefreshTokens、idTokenSignatureAlgorithm 等',
  PRIMARY KEY (id),
  UNIQUE KEY uk_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 客户端注册信息（一条 = 一个接入方）';


-- ------------------------------------------------------------
-- 2. 授权记录表（OAuth2Authorization）
--    一条记录 = 一次授权流程的全生命周期：授权码 → 访问令牌 → 刷新令牌 → id_token。
--    未启用 JdbcOAuth2AuthorizationService 时该表为空、令牌存内存，重启即全部失效。
--    表会随令牌签发/刷新持续增长，需定期清理过期行（见文件末尾清理示例）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_authorization (
  id                            VARCHAR(100) NOT NULL COMMENT '主键：本次授权记录 ID（UUID 字符串），与令牌无关',
  registered_client_id          VARCHAR(100) NOT NULL COMMENT '所属客户端：关联 oauth2_registered_client.id（注意是内部主键，不是 client_id）',
  principal_name                VARCHAR(200) NOT NULL COMMENT '资源所有者标识：即登录用户名（本实现取 basic_user.username），令牌 sub 在签发时被改写为 uuid',
  authorization_grant_type      VARCHAR(100) NOT NULL COMMENT '本次授权使用的模式：authorization_code / client_credentials / refresh_token 等',
  authorized_scopes             VARCHAR(1000) DEFAULT NULL COMMENT '用户最终授予的 scope，逗号分隔；未弹确认页时等于申请值',
  attributes                    BLOB DEFAULT NULL COMMENT '授权会话上下文（JSON 字节流）：含 Principal、OAuth2AuthorizationRequest、当前认证信息等',
  state                         VARCHAR(500) DEFAULT NULL COMMENT '授权请求的 state 原值：回调时用于防 CSRF，校验后不再使用',
  authorization_code_value      BLOB DEFAULT NULL COMMENT '授权码明文（字节流）；一次性使用，换令牌后写入 metadata 标记已失效',
  authorization_code_issued_at  TIMESTAMP DEFAULT NULL COMMENT '授权码签发时间',
  authorization_code_expires_at TIMESTAMP DEFAULT NULL COMMENT '授权码过期时间（默认 5 分钟，OAuth2AuthorizationCodeRequestAuthenticationProvider 可配）',
  authorization_code_metadata   BLOB DEFAULT NULL COMMENT '授权码元数据（JSON 字节流）：invalidated、claims、issuedAt/expiresAt 冗余值',
  access_token_value            BLOB DEFAULT NULL COMMENT '访问令牌明文（JWT 字节流）；资源服务器验签用的就是它',
  access_token_issued_at        TIMESTAMP DEFAULT NULL COMMENT '访问令牌签发时间（JWT 的 iat）',
  access_token_expires_at       TIMESTAMP DEFAULT NULL COMMENT '访问令牌过期时间（JWT 的 exp）；默认 2 小时，见 DemoRegisteredClientInitializer',
  access_token_metadata         BLOB DEFAULT NULL COMMENT '访问令牌元数据（JSON 字节流）：claims、token_type、scopes、invalidated 等',
  access_token_type             VARCHAR(100) DEFAULT NULL COMMENT '令牌类型：Bearer（当前仅支持 Bearer）',
  access_token_scopes           VARCHAR(1000) DEFAULT NULL COMMENT '该访问令牌实际包含的 scope，逗号分隔（决定 userinfo 可返回哪些字段）',
  oidc_id_token_value           BLOB DEFAULT NULL COMMENT 'OIDC id_token（JWT 字节流）：仅 scope 含 openid 时才有值',
  oidc_id_token_issued_at       TIMESTAMP DEFAULT NULL COMMENT 'id_token 签发时间',
  oidc_id_token_expires_at      TIMESTAMP DEFAULT NULL COMMENT 'id_token 过期时间',
  oidc_id_token_metadata        BLOB DEFAULT NULL COMMENT 'id_token 元数据（JSON 字节流）：claims（含 sub/nickname/authorities）、nonce 等',
  refresh_token_value           BLOB DEFAULT NULL COMMENT '刷新令牌明文（字节流）：不透明字符串，非 JWT',
  refresh_token_issued_at       TIMESTAMP DEFAULT NULL COMMENT '刷新令牌签发时间',
  refresh_token_expires_at      TIMESTAMP DEFAULT NULL COMMENT '刷新令牌过期时间；默认 7 天，且 reuseRefreshTokens=false 时每次刷新都轮换',
  refresh_token_metadata        BLOB DEFAULT NULL COMMENT '刷新令牌元数据（JSON 字节流）：invalidated、claims 等',
  user_code_value               BLOB DEFAULT NULL COMMENT '设备码模式：用户码（如 WDJB-MJHT），供用户在验证页输入',
  user_code_issued_at           TIMESTAMP DEFAULT NULL COMMENT '用户码签发时间',
  user_code_expires_at          TIMESTAMP DEFAULT NULL COMMENT '用户码过期时间',
  user_code_metadata            BLOB DEFAULT NULL COMMENT '用户码元数据（JSON 字节流）',
  device_code_value             BLOB DEFAULT NULL COMMENT '设备码模式：设备码，设备端轮询令牌端点时携带',
  device_code_issued_at         TIMESTAMP DEFAULT NULL COMMENT '设备码签发时间',
  device_code_expires_at        TIMESTAMP DEFAULT NULL COMMENT '设备码过期时间',
  device_code_metadata          BLOB DEFAULT NULL COMMENT '设备码元数据（JSON 字节流）',
  PRIMARY KEY (id),
  INDEX idx_client_id (registered_client_id),
  INDEX idx_principal_name (principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='授权记录（授权码/访问令牌/刷新令牌/id_token 的完整生命周期）';


-- ------------------------------------------------------------
-- 3. JWT 签名密钥表（本项目扩展表，非 Spring 官方）
--    默认 store-type=database 时首次启动生成 RSA 2048 密钥对并入库，之后复用；
--    多实例部署共用同一条记录，否则 A 实例签发的令牌 B 实例验不过。
--    ⚠️ jwk_set_json 内含私钥：生产环境应对该列加密存储，并限制库账号仅能读写本表。
--    ⚠️ 删除该行会导致已签发的所有令牌失效（密钥重新生成，JWKS 公钥随之变化）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_jwk (
  id            VARCHAR(100) NOT NULL COMMENT '密钥ID（kid）：取值 autumn.authorization-server.jwk.key-id，多个实例共用同一条',
  jwk_set_json  TEXT NOT NULL COMMENT 'JWK Set JSON（含 RSA 私钥）：由 JwkSupport.serialize 写入，读取时校验私钥存在',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次生成时间',
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间（密钥轮换时变更）',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT 签名密钥对（含私钥，注意加密与授权）';


-- ------------------------------------------------------------
-- 4. 用户授权同意记录（OAuth2AuthorizationConsent）
--    client_settings.requireAuthorizationConsent=true 时，用户首次确认授权后写入，
--    之后同一用户+同一客户端不再重复弹确认页。
--    联合主键（客户端, 用户名），因此同一客户端对同一用户只有一条。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
  registered_client_id VARCHAR(100) NOT NULL COMMENT '客户端：关联 oauth2_registered_client.id（联合主键之一）',
  principal_name       VARCHAR(200) NOT NULL COMMENT '授权用户：登录用户名（联合主键之一）',
  authorities          VARCHAR(1000) NOT NULL COMMENT '用户已同意的 scope 集合，逗号分隔；未列出的 scope 下次仍需确认',
  PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户授权同意记录（同一用户+客户端仅一条）';


-- ============================================================
-- 运维参考（勿随启动自动执行，按需手工跑）：
--   1) 清理过期授权记录（保留 1 天缓冲，避免正在刷新的令牌被误删）
--      DELETE FROM oauth2_authorization
--       WHERE COALESCE(refresh_token_expires_at, access_token_expires_at, authorization_code_expires_at)
--             < DATE_SUB(NOW(), INTERVAL 1 DAY);
--   2) 查某用户当前所有有效令牌
--      SELECT registered_client_id, authorization_grant_type, access_token_scopes, access_token_expires_at
--        FROM oauth2_authorization
--       WHERE principal_name = 'admin' AND access_token_expires_at > NOW();
--   3) 密钥轮换：插入新 kid 后需保证 JWKS 端点同时暴露新旧公钥，
--      否则旧令牌在旧公钥下线瞬间全部失效（当前实现为单密钥，轮换=先删后重启）。
-- ============================================================
