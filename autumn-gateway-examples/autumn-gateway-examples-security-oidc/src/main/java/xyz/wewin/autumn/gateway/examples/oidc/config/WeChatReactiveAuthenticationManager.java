package xyz.wewin.autumn.gateway.examples.oidc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.userinfo.DefaultReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;
import reactor.core.publisher.Mono;

/**
 * 委托式 OAuth2 响应式认证管理器
 * <p>
 * 根据 registrationId 将微信登录路由到自定义流程，其他标准 OIDC Provider（Google、GitHub 等）
 * 使用 Spring Security 内置的 token 交换 + {@link DefaultReactiveOAuth2UserService} 处理。
 * <p>
 * 微信认证流程（自定义）：
 * <ol>
 *   <li>{@link WeChatOAuth2AccessTokenResponseClient} — code → access_token（微信 token 接口非标准）</li>
 *   <li>{@link WeChatReactiveOAuth2UserService} — access_token + openid → 用户信息（微信 userinfo 接口非标准）</li>
 *   <li>构建 {@link OAuth2AuthenticationToken} 返回给 Security 框架</li>
 * </ol>
 * <p>
 * 标准 OIDC 认证流程（Google / GitHub）：
 * <ol>
 *   <li>{@link WebClientReactiveAuthorizationCodeTokenResponseClient} — code → access_token（标准 POST）</li>
 *   <li>{@link DefaultReactiveOAuth2UserService} — 自动获取用户信息（标准 OIDC UserInfo）</li>
 *   <li>构建 {@link OAuth2AuthenticationToken} 返回给 Security 框架</li>
 * </ol>
 */
public class WeChatReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private static final Logger log = LoggerFactory.getLogger(WeChatReactiveAuthenticationManager.class);
    private static final String WECHAT_REGISTRATION_ID = "wechat";

    private final WeChatOAuth2AccessTokenResponseClient wechatTokenClient;
    private final WeChatReactiveOAuth2UserService wechatUserService;

    /** 标准 OIDC token 交换客户端 */
    private final ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> defaultTokenClient;
    /** 标准 OIDC 用户信息服务（支持 OIDC id_token 和标准 OAuth2 userinfo） */
    private final ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> defaultUserService;

    public WeChatReactiveAuthenticationManager(
            WeChatOAuth2AccessTokenResponseClient wechatTokenClient,
            WeChatReactiveOAuth2UserService wechatUserService) {
        this.wechatTokenClient = wechatTokenClient;
        this.wechatUserService = wechatUserService;
        this.defaultTokenClient = new WebClientReactiveAuthorizationCodeTokenResponseClient();
        this.defaultUserService = new DefaultReactiveOAuth2UserService();
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthorizationCodeAuthenticationToken codeToken)) {
            return Mono.empty();
        }

        String registrationId = codeToken.getClientRegistration().getRegistrationId();

        if (WECHAT_REGISTRATION_ID.equals(registrationId)) {
            log.info("Processing WeChat OAuth2 login, registrationId={}", registrationId);
            return authenticateWeChat(codeToken);
        }

        // 标准 OIDC Provider（Google / GitHub 等）
        log.info("Processing standard OIDC login, registrationId={}", registrationId);
        return authenticateStandard(codeToken);
    }

    /**
     * 微信专用认证流程
     */
    private Mono<Authentication> authenticateWeChat(OAuth2AuthorizationCodeAuthenticationToken codeToken) {
        var registration = codeToken.getClientRegistration();
        var grantRequest = new OAuth2AuthorizationCodeGrantRequest(
                registration, codeToken.getAuthorizationExchange());

        return wechatTokenClient.getTokenResponse(grantRequest)
                .flatMap(tokenResponse -> {
                    var accessToken = tokenResponse.getAccessToken();

                    // 将 openid 等微信特有参数通过 additionalParameters 传递给 UserService
                    var userRequest = new OAuth2UserRequest(
                            registration, accessToken, tokenResponse.getAdditionalParameters());

                    log.info("Fetching WeChat user info, openid={}",
                            tokenResponse.getAdditionalParameters().get("openid"));

                    return wechatUserService.loadUser(userRequest)
                            .map(oauth2User -> (Authentication) new OAuth2AuthenticationToken(
                                    oauth2User,
                                    oauth2User.getAuthorities(),
                                    registration.getRegistrationId()
                            ));
                });
    }

    /**
     * 标准 OIDC 认证流程（Google / GitHub 等）
     * <p>
     * 使用 Spring Security 内置的 token 交换和用户信息服务，
     * 完整复刻 OAuth2AuthorizationCodeReactiveAuthenticationManager 的逻辑，
     * 并补充用户信息获取（因为 Spring Security 7.0+ 中 authenticationManager 需要接管完整流程）。
     */
    private Mono<Authentication> authenticateStandard(OAuth2AuthorizationCodeAuthenticationToken codeToken) {
        var registration = codeToken.getClientRegistration();
        var grantRequest = new OAuth2AuthorizationCodeGrantRequest(
                registration, codeToken.getAuthorizationExchange());

        return defaultTokenClient.getTokenResponse(grantRequest)
                .flatMap(tokenResponse -> {
                    OAuth2AccessToken accessToken = tokenResponse.getAccessToken();

                    // 使用 DefaultReactiveOAuth2UserService 获取用户信息
                    // 它会自动判断是 OIDC（有 id_token）还是标准 OAuth2（走 userinfo endpoint）
                    var userRequest = new OAuth2UserRequest(
                            registration, accessToken, tokenResponse.getAdditionalParameters());

                    log.info("Fetching user info for standard OIDC provider, registrationId={}",
                            registration.getRegistrationId());

                    return defaultUserService.loadUser(userRequest)
                            .map(oauth2User -> (Authentication) new OAuth2AuthenticationToken(
                                    oauth2User,
                                    oauth2User.getAuthorities(),
                                    registration.getRegistrationId()
                            ));
                });
    }
}
