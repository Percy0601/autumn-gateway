package xyz.wewin.autumn.gateway.authorization.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.util.StringUtils;
import xyz.wewin.autumn.gateway.authorization.security.UserAccountQuery;

import java.util.function.Function;

/**
 * 授权服务器核心配置（Spring Security 7.x / Authorization Server 7.x）
 * 对应职责：OAuth2 令牌颁发、客户端管理、OIDC 协议
 *
 * <p>注意：Spring Security 7 起，授权服务器配置类从
 * {@code oauth2.server.authorization.config} 迁移到
 * {@code config.annotation.web.configurers.oauth2.server.authorization}，
 * 且 {@code applyDefaultSecurity()} 已被移除，需显式声明过滤器链。</p>
 */
@Configuration
public class AuthorizationServerConfig {

    /**
     * 签发者地址（issuer）。经网关/反代之后必须显式配置，否则令牌 iss 与实际访问地址不一致；
     * 留空时按当前请求自动推导。
     */
    @Value("${autumn.authorization-server.issuer:}")
    private String issuer;

    private final UserAccountQuery userAccountQuery;

    public AuthorizationServerConfig(UserAccountQuery userAccountQuery) {
        this.userAccountQuery = userAccountQuery;
    }

    /**
     * 授权服务器安全过滤器链（优先级最高）
     * 只处理 /oauth2/**、/.well-known/**、/userinfo 等协议端点
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        http
                // 仅匹配协议端点，其余请求交给 defaultSecurityFilterChain
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                // 应用授权服务器配置，并启用 OIDC
                .with(authorizationServerConfigurer, (authorizationServer) ->
                        authorizationServer
                                .oidc((oidc) -> oidc
                                        .userInfoEndpoint((userInfo) -> userInfo
                                                .userInfoMapper(userInfoMapper()))))
                .authorizeHttpRequests((authorize) -> authorize
                        .anyRequest().authenticated())
                // 浏览器访问（如 /oauth2/authorize）未登录时跳转登录页；
                // 非浏览器请求（如 /userinfo、/oauth2/introspect）仍返回 401，符合协议规范
                .exceptionHandling((exceptions) -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                // userinfo / introspection 等端点需要校验 bearer token
                .oauth2ResourceServer((resourceServer) -> resourceServer
                        .jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * 客户端仓储：从 oauth2_registered_client 表读取
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * 授权记录持久化：不配置的话默认走内存，重启后已发放的令牌全部失效
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService(
                jdbcTemplate, registeredClientRepository);
    }

    /**
     * 授权同意记录持久化
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService(
                jdbcTemplate, registeredClientRepository);
    }

    /**
     * 授权服务器设置（必需组件）。7.x 不再有 applyDefaultSecurity 兜底，必须显式注册。
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        AuthorizationServerSettings.Builder builder = AuthorizationServerSettings.builder();
        if (StringUtils.hasText(issuer)) {
            builder.issuer(issuer);
        }
        return builder.build();
    }

    /**
     * userinfo / OIDC 动态注册端点需要用本服务器公钥校验 access_token（JWT）
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * 令牌定制：把 sub 换成用户的 uuid（对外统一标识，不随用户名变化），
     * 并附带昵称、邮箱、角色等业务字段
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(UserAccountQuery userAccountQuery) {
        return (context) -> {
            Authentication principal = context.getPrincipal();
            if (principal == null) {
                return;
            }
            // 客户端凭证模式下 principal 是客户端，库里查不到对应用户，保持默认 sub 即可
            userAccountQuery.findByUsername(principal.getName()).ifPresent((account) -> context.getClaims()
                    .subject(account.uuid())
                    .claim("username", account.username())
                    .claim("nickname", account.nickname())
                    .claim("email", account.email())
                    .claim("authorities", account.authorities()));
        };
    }

    /**
     * OIDC userinfo 响应：sub 必须与 id_token 一致（都取 uuid），否则客户端校验不通过
     */
    private Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper() {
        return (context) -> {
            OAuth2Authorization authorization = context.getAuthorization();
            String principalName = authorization.getPrincipalName();
            OidcUserInfo.Builder builder = OidcUserInfo.builder().subject(principalName);

            userAccountQuery.findByUsername(principalName).ifPresent((account) -> builder
                    .subject(account.uuid())
                    .name(account.nickname())
                    .email(account.email())
                    .phoneNumber(account.phone())
                    .claim("username", account.username())
                    .claim("authorities", account.authorities()));

            return builder.build();
        };
    }
}
