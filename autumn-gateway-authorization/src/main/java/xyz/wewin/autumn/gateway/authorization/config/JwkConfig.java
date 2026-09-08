package xyz.wewin.autumn.gateway.authorization.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * JWKS 密钥配置：管理 JWT 签名的 RSA 密钥对
 * 对应职责：JWKS 密钥
 *
 * <p>【保留的原始实现】每次启动在内存中随机生成密钥对，仅用于本地演示/一次性环境。
 * 缺点是重启即换密钥，已签发的令牌全部失效；多实例部署时各实例密钥不同，令牌互相验不过。</p>
 *
 * <p>默认不启用。生产请用
 * {@code autumn.authorization-server.jwk.store-type=database}（见 {@link JdbcJwkConfig}）
 * 或 {@code file}（见 {@link FileJwkConfig}）。</p>
 */
@Configuration
@ConditionalOnProperty(name = "autumn.authorization-server.jwk.store-type", havingValue = "memory")
public class JwkConfig {

    private static final Logger log = LoggerFactory.getLogger(JwkConfig.class);

    /**
     * 提供 JWK 源，授权服务器用它签名令牌，同时对外暴露 /oauth2/jwks 公钥端点
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        log.warn("当前使用内存随机密钥（store-type=memory），重启后已签发的令牌将全部失效，请勿用于生产");
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString()) // 密钥ID，用于多密钥轮换
                .build();

        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /**
     * 生成 RSA 2048 位密钥对（示例用内存生成）
     * 生产环境请从密钥库文件 / KMS / 配置中心加载，不要硬编码
     */
    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("生成RSA密钥失败", e);
        }
    }
}
