package xyz.wewin.autumn.gateway.examples.oidc.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import xyz.wewin.autumn.gateway.examples.oidc.util.KeyUtils;

import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Autowired
    DynamicReactiveAuthorizationManager dynamicAuthManager;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            // JWT Resource Server：验证下游服务传递的 JWT Token
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                            .jwtDecoder(reactiveJwtDecoder())
                    )
            )
            // 授权规则
            .authorizeExchange(exchanges -> exchanges
                    .pathMatchers(HttpMethod.OPTIONS).permitAll()
                    .pathMatchers("/", "/login", "/login.html", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                    .pathMatchers("/auth/login").permitAll()
                    .pathMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                    .anyExchange().access(dynamicAuthManager)
            )
            // OAuth2 Login：通过 authenticationManager 统一管理所有 Provider
            // Spring Security 7.0 移除了 tokenEndpoint() / userInfoEndpoint()，
            // 因此使用 authenticationManager() 注入委托式认证管理器
            .oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler("/auth/oauth2-success"))
                    .authenticationManager(wechatReactiveAuthenticationManager())
            );

        return http.build();
    }

    /**
     * 委托式 OAuth2 认证管理器
     * - 微信 → 自定义 TokenClient + UserService
     * - Google/GitHub 等标准 OIDC → Spring Security 内置流程
     */
    @Bean
    public WeChatReactiveAuthenticationManager wechatReactiveAuthenticationManager() {
        return new WeChatReactiveAuthenticationManager(
                wechatAccessTokenResponseClient(),
                wechatReactiveOAuth2UserService()
        );
    }

    /**
     * 微信 access_token 获取客户端
     * 微信 token 接口不符合 OAuth2 标准（使用 GET + query 参数），需自定义实现
     */
    @Bean
    public WeChatOAuth2AccessTokenResponseClient wechatAccessTokenResponseClient() {
        return new WeChatOAuth2AccessTokenResponseClient();
    }

    /**
     * 微信用户信息服务
     * 微信 userinfo 接口不符合 OAuth2/OIDC 标准（使用 GET + query 参数传递 access_token 和 openid），
     * 需自定义实现
     */
    @Bean
    public WeChatReactiveOAuth2UserService wechatReactiveOAuth2UserService() {
        return new WeChatReactiveOAuth2UserService();
    }

    /**
     * JWT 解码器（用于 Resource Server 验证下游传递的 JWT）
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        PublicKey publicKey = KeyUtils.loadPublicKey();
        return NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) publicKey).build();
    }

    @Bean
    public ReactiveClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration wechat = ClientRegistration.withRegistrationId("wechat")
                .clientId("wx你的真实appid")
                .clientSecret("你的真实secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("snsapi_login")
                .authorizationUri("https://open.weixin.qq.com/connect/qrconnect")
                .tokenUri("https://api.weixin.qq.com/sns/oauth2/access_token")
                .userInfoUri("https://api.weixin.qq.com/sns/userinfo")
                .userNameAttributeName("openid")
                .clientName("WeChat")
                .build();
        return new InMemoryReactiveClientRegistrationRepository(wechat);
    }
}
