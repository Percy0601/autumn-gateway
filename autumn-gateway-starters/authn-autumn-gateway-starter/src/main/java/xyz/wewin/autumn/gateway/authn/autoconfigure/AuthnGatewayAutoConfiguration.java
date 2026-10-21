package xyz.wewin.autumn.gateway.authn.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * 网关统一认证（AuthN）自动配置。
 *
 * <h3>它做了什么</h3>
 * <ol>
 *   <li>注册 OAuth2 客户端（授权码 + PKCE），登录入口 {@code /oauth2/authorization/{registration-id}}；</li>
 *   <li>未认证请求 → 302 到授权服务器；XHR / JSON 请求 → 直接 401（避免 SPA 拿到登录页 HTML）；</li>
 *   <li>令牌存放在<b>服务端会话</b>（WebSession）里，浏览器只拿 HttpOnly 的会话 Cookie；</li>
 *   <li>令牌下发给下游服务由 {@code TokenRelay} 过滤器完成（Spring Cloud Gateway 内置），
 *       它会把 access_token 以 {@code Authorization: Bearer} 注入转发请求。</li>
 * </ol>
 *
 * <h3>两个刻意的设计决定</h3>
 * <ul>
 *   <li><b>不配置 {@code issuer-uri} 做 OIDC 发现</b>，而是显式拼出各端点 URI ——
 *       发现动作发生在启动期，会让网关启动依赖授权服务器已就绪，违背「网关可独立重启」。</li>
 *   <li><b>显式 {@code requireProofKey(true)}</b>：Spring Security 的 reactive 授权请求解析器
 *       只对「public client（client_authentication_method=none）」自动加 PKCE，
 *       而 Spring Authorization Server 7（OAuth 2.1）对授权码模式<b>强制要求</b> PKCE，
 *       不显式打开就会收到 {@code invalid_request: OAuth 2.0 Parameter: code_challenge}。</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "autumn.authn", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuthnGatewayProperties.class)
public class AuthnGatewayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthnGatewayAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ReactiveClientRegistrationRepository authnClientRegistrationRepository(AuthnGatewayProperties props) {
        String issuer = trimTrailingSlash(props.getIssuerUri());

        ClientRegistration registration = ClientRegistration.withRegistrationId(props.getRegistrationId())
                .clientId(props.getClientId())
                .clientSecret(props.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(props.getRedirectUriTemplate())
                .scope(props.resolvedScopes())
                .issuerUri(issuer)
                .authorizationUri(issuer + "/oauth2/authorize")
                .tokenUri(issuer + "/oauth2/token")
                .jwkSetUri(issuer + "/oauth2/jwks")
                .userInfoUri(issuer + "/userinfo")
                .userNameAttributeName("sub")
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                .build();

        log.info("网关统一认证已启用：登录入口 /oauth2/authorization/{}，授权服务器 {}，PKCE 已开启",
                props.getRegistrationId(), issuer);
        return new InMemoryReactiveClientRegistrationRepository(registration);
    }

    /**
     * 令牌存放位置：服务端会话。浏览器侧只有 SESSION Cookie，拿不到 access_token。
     *
     * <p>多实例部署时需要 Spring Session（JDBC / Redis）做会话共享，否则会要求粘性会话。
     * 这是「网关作为 OAuth2 客户端」这个选型的唯一代价。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public ServerOAuth2AuthorizedClientRepository authnAuthorizedClientRepository() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityWebFilterChain.class)
    public SecurityWebFilterChain authnSecurityWebFilterChain(ServerHttpSecurity http,
                                                              AuthnGatewayProperties props,
                                                              ReactiveClientRegistrationRepository clientRegistrationRepository,
                                                              ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {
        ServerAuthenticationEntryPoint entryPoint = new DelegatingServerAuthenticationEntryPoint(
                new DelegatingServerAuthenticationEntryPoint.DelegateEntry(
                        jsonOrXhrMatcher(),
                        new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)));
        ((DelegatingServerAuthenticationEntryPoint) entryPoint).setDefaultEntryPoint(
                new RedirectServerAuthenticationEntryPoint("/oauth2/authorization/" + props.getRegistrationId()));

        http
                .csrf((csrf) -> csrf.disable())
                .authorizeExchange((exchange) -> {
                    for (String pattern : props.getPermitAll()) {
                        exchange.pathMatchers(pattern).permitAll();
                    }
                    exchange.anyExchange().authenticated();
                })
                .oauth2Login((login) -> login
                        .clientRegistrationRepository(clientRegistrationRepository)
                        .authorizedClientRepository(authorizedClientRepository))
                .logout((logout) -> logout
                        // 显式放行 GET + POST。
                        // Spring Security 7 的 WebFlux 登出默认只认 POST，
                        // 而前端「退出登录」是一次普通跳转（GET），不改就会 404。
                        // 注意 API 名：reactive 侧是 requiresLogout(...)，不是 logoutRequestMatcher(...)。
                        .requiresLogout(logoutRequestMatcher(props.getLogoutUrl()))
                        .logoutSuccessHandler(logoutSuccessHandler(props.getLogoutSuccessUrl())))
                .exceptionHandling((ex) -> ex.authenticationEntryPoint(entryPoint));

        return http.build();
    }

    /**
     * XHR / JSON 请求直接回 401，而不是 302 到登录页。
     *
     * <p>否则 SPA 的 axios 会跟随重定向拿到登录页 HTML（HTTP 200），
     * 前端既判不出未登录，又会因为 JSON.parse 失败报一堆莫名其妙的错。</p>
     */
    private static ServerWebExchangeMatcher jsonOrXhrMatcher() {
        return (exchange) -> {
            List<MediaType> accept = exchange.getRequest().getHeaders().getAccept();
            boolean jsonPreferred = !accept.isEmpty()
                    && accept.stream().anyMatch((media) -> media.isCompatibleWith(MediaType.APPLICATION_JSON));
            boolean xhr = "XMLHttpRequest"
                    .equalsIgnoreCase(exchange.getRequest().getHeaders().getFirst("X-Requested-With"));
            return (jsonPreferred || xhr)
                    ? ServerWebExchangeMatcher.MatchResult.match()
                    : ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }

    /**
     * 登出入口：GET（前端「退出登录」是普通跳转）与 POST 都认。
     *
     * <p>这里刻意<b>不限制 HttpMethod</b>：实测 {@code pathMatchers(HttpMethod.GET, url)} 组合进
     * OrServerWebExchangeMatcher 后 GET 反而匹配不上（POST 正常），只按路径匹配最稳。
     * 代价是 CSRF 式的「诱导登出」成为可能 —— 网关已全局关闭 CSRF，接受这个取舍；
     * 若日后要收紧，改成只认 POST + 前端发 POST 即可。</p>
     */
    private static ServerWebExchangeMatcher logoutRequestMatcher(String logoutUrl) {
        return ServerWebExchangeMatchers.pathMatchers(logoutUrl);
    }

    private static ServerLogoutSuccessHandler logoutSuccessHandler(String url) {
        RedirectServerLogoutSuccessHandler handler = new RedirectServerLogoutSuccessHandler();
        try {
            handler.setLogoutSuccessUrl(new URI(url));
        } catch (URISyntaxException e) {
            log.warn("登出跳转地址非法（{}），回落到 /", url);
            handler.setLogoutSuccessUrl(URI.create("/"));
        }
        return handler;
    }

    private static String trimTrailingSlash(String uri) {
        return (uri != null && uri.endsWith("/")) ? uri.substring(0, uri.length() - 1) : uri;
    }
}
