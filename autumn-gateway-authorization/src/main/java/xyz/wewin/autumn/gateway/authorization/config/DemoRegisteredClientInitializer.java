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
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    /** 逗号分隔，可登记多个回调地址（网关 OAuth2 客户端 / 直连调试各一个） */
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

        // 写入 oauth2_registered_client：withId 存 id 列，clientId/clientSecret 存同名两列。
        // 密钥必须存哈希（PasswordEncoder 校验时会用 encode 后的值比对明文），明文只用于日志提示。
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(this.clientId)
                .clientSecret(this.passwordEncoder.encode(this.clientSecret))
                // client_authentication_methods 列：BASIC = 走 Authorization 头传 client_id/secret
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                // authorization_grant_types 列：三个模式拼成逗号分隔字符串
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                // scopes 列：openid 决定是否签发 id_token（OIDC），缺了它 userinfo 不可用
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("business:read");

        // redirect_uris 列：多个回调拼成逗号分隔字符串，授权请求必须精确命中其中之一
        for (String uri : resolveRedirectUris()) {
            builder.redirectUri(uri);
        }

        // token_settings 列：整段序列化成 JSON 存库，改这里等于改令牌 TTL
        builder.tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(2))
                .refreshTokenTimeToLive(Duration.ofDays(7))
                // 关闭刷新令牌复用：每次刷新都签发新 refresh_token（旧的可被检测重放）
                .reuseRefreshTokens(false)
                .build());

        this.registeredClientRepository.save(builder.build());
        log.warn("已初始化演示客户端 {} / {}，回调地址 {}（生产环境请关闭 autumn.authorization-server.demo-client.enabled）",
                this.clientId, this.clientSecret, resolveRedirectUris());
    }

    /** 逗号分隔拆成多个回调地址；空白项丢弃，全空时回落到默认值 */
    private List<String> resolveRedirectUris() {
        List<String> uris = new ArrayList<>();
        if (StringUtils.hasText(this.redirectUri)) {
            for (String part : this.redirectUri.split(",")) {
                if (StringUtils.hasText(part)) {
                    uris.add(part.trim());
                }
            }
        }
        if (uris.isEmpty()) {
            uris.add("http://127.0.0.1:8080/callback");
        }
        return uris;
    }
}
