package xyz.wewin.autumn.gateway.authorization.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 演示用客户端初始化：首次启动时把 web-client 写入 oauth2_registered_client。
 *
 * <p>仅用于本地联调，生产环境请设置
 * {@code autumn.authorization-server.demo-client.enabled=false}，
 * 由管理后台/dashboard 维护客户端。</p>
 */
@Component
public class DemoRegisteredClientInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRegisteredClientInitializer.class);

    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${autumn.authorization-server.demo-client.enabled:false}")
    private boolean enabled;

    @Value("${autumn.authorization-server.demo-client.client-id:web-client}")
    private String clientId;

    @Value("${autumn.authorization-server.demo-client.client-secret:secret}")
    private String clientSecret;

    @Value("${autumn.authorization-server.demo-client.redirect-uri:http://127.0.0.1:8080/callback}")
    private String redirectUri;

    public DemoRegisteredClientInitializer(RegisteredClientRepository registeredClientRepository,
                                           PasswordEncoder passwordEncoder) {
        this.registeredClientRepository = registeredClientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (this.registeredClientRepository.findByClientId(this.clientId) != null) {
            return;
        }

        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(this.clientId)
                .clientSecret(this.passwordEncoder.encode(this.clientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri(this.redirectUri)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("business:read")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(2))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        this.registeredClientRepository.save(registeredClient);
        log.warn("已初始化演示客户端 {} / {}（生产环境请关闭 autumn.authorization-server.demo-client.enabled）",
                this.clientId, this.clientSecret);
    }
}
