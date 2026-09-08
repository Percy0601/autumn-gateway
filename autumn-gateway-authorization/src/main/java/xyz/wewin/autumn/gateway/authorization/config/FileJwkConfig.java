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
import xyz.wewin.autumn.gateway.authorization.security.JwkSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件持久化的 JWKS 密钥。
 *
 * <p>适合单实例、不方便建表或密钥由外部（KMS / 配置中心）下发的场景。
 * 注意：native image 部署时要确保该路径可读写。</p>
 */
@Configuration
@ConditionalOnProperty(name = "autumn.authorization-server.jwk.store-type", havingValue = "file")
public class FileJwkConfig {

    private static final Logger log = LoggerFactory.getLogger(FileJwkConfig.class);

    @Value("${autumn.authorization-server.jwk.file-path:./config/authorization-jwk.json}")
    private String filePath;

    @Value("${autumn.authorization-server.jwk.key-id:autumn-gateway-authorization-key}")
    private String keyId;

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws IOException {
        Path path = Path.of(this.filePath);
        if (Files.exists(path) && Files.size(path) > 0) {
            JWKSet jwkSet = JwkSupport.parse(Files.readString(path, StandardCharsets.UTF_8));
            log.info("已加载 JWKS 密钥：keyId={}，来源=file（{}）", this.keyId, path.toAbsolutePath());
            return new ImmutableJWKSet<>(jwkSet);
        }

        JWKSet jwkSet = JwkSupport.generate(this.keyId);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, JwkSupport.serialize(jwkSet), StandardCharsets.UTF_8);
        log.warn("未找到密钥文件，已生成新的 JWKS 密钥：{}（请妥善备份，丢失后已签发令牌将全部失效）",
                path.toAbsolutePath());
        return new ImmutableJWKSet<>(jwkSet);
    }
}
