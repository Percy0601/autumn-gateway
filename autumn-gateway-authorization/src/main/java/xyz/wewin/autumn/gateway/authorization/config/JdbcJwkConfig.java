package xyz.wewin.autumn.gateway.authorization.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import xyz.wewin.autumn.gateway.authorization.security.JwkSupport;

/**
 * 数据库持久化的 JWKS 密钥（默认方案）。
 *
 * <p>为什么必须持久化：
 * <ul>
 *   <li>重启后密钥不变，已签发的令牌不会全部失效；</li>
 *   <li>多实例部署时大家用同一把密钥，否则 A 实例签的令牌 B 实例验不过。</li>
 * </ul>
 * 首次启动自动生成并入库，之后直接复用。
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "autumn.authorization-server.jwk.store-type",
        havingValue = "database", matchIfMissing = true)
public class JdbcJwkConfig {

    private static final Logger log = LoggerFactory.getLogger(JdbcJwkConfig.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${autumn.authorization-server.jwk.key-id:autumn-gateway-authorization-key}")
    private String keyId;

    public JdbcJwkConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        JWKSet jwkSet = loadOrCreate();
        log.info("已加载 JWKS 密钥：keyId={}，来源=database", this.keyId);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private JWKSet loadOrCreate() {
        String json = queryJson();
        if (StringUtils.hasText(json)) {
            return JwkSupport.parse(json);
        }

        // 首次启动（或多实例并发）时生成；并发场景下只有一个实例能插入成功
        String generated = JwkSupport.serialize(JwkSupport.generate(this.keyId));
        try {
            this.jdbcTemplate.update(
                    "INSERT INTO oauth2_jwk (id, jwk_set_json, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
                    this.keyId, generated);
        } catch (DuplicateKeyException ex) {
            log.debug("密钥已由其他实例创建，直接复用");
        }

        json = queryJson();
        if (StringUtils.hasText(json)) {
            return JwkSupport.parse(json);
        }
        throw new IllegalStateException("JWKS 密钥初始化失败：oauth2_jwk 中未找到 id=" + this.keyId);
    }

    private String queryJson() {
        return this.jdbcTemplate.query(
                "SELECT jwk_set_json FROM oauth2_jwk WHERE id = ?",
                (rs) -> rs.next() ? rs.getString("jwk_set_json") : null,
                this.keyId);
    }
}
