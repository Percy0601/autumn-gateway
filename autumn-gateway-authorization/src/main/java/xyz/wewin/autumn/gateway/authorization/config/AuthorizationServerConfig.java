package xyz.wewin.autumn.gateway.authorization.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.time.Duration;
import java.util.UUID;

/**
 * 授权服务器核心配置
 * 对应职责：OAuth2令牌颁发、客户端管理、OIDC协议
 */
@Configuration
public class AuthorizationServerConfig {

    /**
     * 授权服务器安全过滤器链（优先级最高）
     * 处理 /oauth2/**、/.well-known/**、/userinfo 等协议端点
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // 应用授权服务器默认安全配置
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                // 启用 OIDC 协议（自动开启元数据端点、userinfo 端点）
                .oidc(Customizer.withDefaults());

        // 未登录时重定向到登录页
        http.exceptionHandling(ex ->
                ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
        );

        // 开启 JWT 资源服务器能力（用于 userinfo 等端点自身校验 token）
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * 客户端管理（示例用内存存储）
     * 对应职责：客户端管理
     * 生产环境请使用 JdbcRegisteredClientRepository 持久化到数据库
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient webClient = RegisteredClient.withId(UUID.randomUUID().toString())
                // 客户端ID
                .clientId("web-client")
                // 客户端密钥（生产环境必须加密存储，这里示例用 BCrypt 加密后的 "secret"）
                .clientSecret("{bcrypt}$2a$10$7aH8vQeNl7dY9zG0w5X6uOY8Z7X9V0W1U2S3R4Q5P6I7U8Y9T0R1E2")
                // 客户端认证方式
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                // 支持的授权模式：授权码、刷新令牌、客户端凭证
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                // 回调地址（必须精确匹配，生产环境不能用通配符）
                .redirectUri("http://127.0.0.1:8080/callback")
                // 授权范围：openid 是 OIDC 必须，profile 是用户信息，自定义业务 scope
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("business:read")
                // 令牌配置
                .tokenSettings(tokenSettings())
                .build();

        return new InMemoryRegisteredClientRepository(webClient);
    }

    /**
     * 令牌全局配置
     */
    @Bean
    public TokenSettings tokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(2))    // access_token 有效期 2小时
                .refreshTokenTimeToLive(Duration.ofDays(7))    // refresh_token 有效期 7天
                .reuseRefreshTokens(false)                     // 刷新令牌后旧 token 失效
                .build();
    }
}
