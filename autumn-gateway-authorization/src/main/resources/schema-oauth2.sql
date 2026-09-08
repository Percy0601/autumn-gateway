-- ============================================================
-- Spring Authorization Server 7.x 持久化表（MySQL 8）
-- 与 dashboard 使用同一个库（autumn-security）
-- 字段与 JdbcRegisteredClientRepository / JdbcOAuth2AuthorizationService
-- / JdbcOAuth2AuthorizationConsentService 的 SQL 严格对应
-- ============================================================

CREATE TABLE IF NOT EXISTS oauth2_registered_client (
  id                            VARCHAR(100) NOT NULL,
  client_id                     VARCHAR(100) NOT NULL,
  client_id_issued_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  client_secret                 VARCHAR(200) DEFAULT NULL,
  client_secret_expires_at      TIMESTAMP DEFAULT NULL,
  client_name                   VARCHAR(200) NOT NULL,
  client_authentication_methods VARCHAR(1000) NOT NULL,
  authorization_grant_types     VARCHAR(1000) NOT NULL,
  redirect_uris                 VARCHAR(1000) DEFAULT NULL,
  post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL,
  scopes                        VARCHAR(1000) NOT NULL,
  client_settings               VARCHAR(2000) NOT NULL,
  token_settings                VARCHAR(2000) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 客户端注册信息';

CREATE TABLE IF NOT EXISTS oauth2_authorization (
  id                            VARCHAR(100) NOT NULL,
  registered_client_id          VARCHAR(100) NOT NULL,
  principal_name                VARCHAR(200) NOT NULL,
  authorization_grant_type      VARCHAR(100) NOT NULL,
  authorized_scopes             VARCHAR(1000) DEFAULT NULL,
  attributes                    BLOB DEFAULT NULL,
  state                         VARCHAR(500) DEFAULT NULL,
  authorization_code_value      BLOB DEFAULT NULL,
  authorization_code_issued_at  TIMESTAMP DEFAULT NULL,
  authorization_code_expires_at TIMESTAMP DEFAULT NULL,
  authorization_code_metadata   BLOB DEFAULT NULL,
  access_token_value            BLOB DEFAULT NULL,
  access_token_issued_at        TIMESTAMP DEFAULT NULL,
  access_token_expires_at       TIMESTAMP DEFAULT NULL,
  access_token_metadata         BLOB DEFAULT NULL,
  access_token_type             VARCHAR(100) DEFAULT NULL,
  access_token_scopes           VARCHAR(1000) DEFAULT NULL,
  oidc_id_token_value           BLOB DEFAULT NULL,
  oidc_id_token_issued_at       TIMESTAMP DEFAULT NULL,
  oidc_id_token_expires_at      TIMESTAMP DEFAULT NULL,
  oidc_id_token_metadata        BLOB DEFAULT NULL,
  refresh_token_value           BLOB DEFAULT NULL,
  refresh_token_issued_at       TIMESTAMP DEFAULT NULL,
  refresh_token_expires_at      TIMESTAMP DEFAULT NULL,
  refresh_token_metadata        BLOB DEFAULT NULL,
  user_code_value               BLOB DEFAULT NULL,
  user_code_issued_at           TIMESTAMP DEFAULT NULL,
  user_code_expires_at          TIMESTAMP DEFAULT NULL,
  user_code_metadata            BLOB DEFAULT NULL,
  device_code_value             BLOB DEFAULT NULL,
  device_code_issued_at         TIMESTAMP DEFAULT NULL,
  device_code_expires_at        TIMESTAMP DEFAULT NULL,
  device_code_metadata          BLOB DEFAULT NULL,
  PRIMARY KEY (id),
  INDEX idx_client_id (registered_client_id),
  INDEX idx_principal_name (principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='授权记录（授权码/令牌）';

-- JWT 签名密钥（含私钥）。生产环境建议对 jwk_set_json 做加密存储，并限制库账号权限
CREATE TABLE IF NOT EXISTS oauth2_jwk (
  id            VARCHAR(100) NOT NULL COMMENT '密钥ID（kid），多个实例共用同一条',
  jwk_set_json  TEXT NOT NULL COMMENT 'JWK Set JSON（含私钥）',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT 签名密钥对';

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
  registered_client_id VARCHAR(100) NOT NULL,
  principal_name       VARCHAR(200) NOT NULL,
  authorities          VARCHAR(1000) NOT NULL,
  PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户授权同意记录';
