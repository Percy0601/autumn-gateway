package xyz.wewin.autumn.gateway.examples.oidc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 微信响应式用户信息服务
 * <p>
 * 负责在拿到 access_token 后，调用微信用户信息接口获取用户属性。
 * 微信的用户信息接口不符合 OAuth2/OIDC 标准：
 * <ul>
 *   <li>使用 GET 请求 + query 参数传递 access_token 和 openid</li>
 *   <li>不使用 Authorization: Bearer 头</li>
 *   <li>返回格式包含 errcode/errmsg 错误码</li>
 * </ul>
 * 因此需要自定义实现，而非使用 DefaultReactiveOAuth2UserService。
 */
public class WeChatReactiveOAuth2UserService implements ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final Logger log = LoggerFactory.getLogger(WeChatReactiveOAuth2UserService.class);

    private static final String WECHAT_USERINFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    private final WebClient webClient = WebClient.builder().build();

    @Override
    @SuppressWarnings("unchecked")
    public Mono<OAuth2User> loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String accessToken = userRequest.getAccessToken().getTokenValue();
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 从 token response 的 additionalParameters 中获取 openid
        // WeChatOAuth2AccessTokenResponseClient 需要将 openid 存入 additionalParameters
        Map<String, Object> additionalParams = userRequest.getAdditionalParameters();
        String openid = (String) additionalParams.get("openid");

        if (openid == null || openid.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_openid", "Missing openid in token response", null),
                    "WeChat user info requires openid, but it was not found in token response");
        }

        String uri = WECHAT_USERINFO_URL + "?access_token=" + accessToken + "&openid=" + openid + "&lang=zh_CN";

        log.info("Fetching WeChat user info, registrationId={}, openid={}", registrationId, openid);

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> {
                    // 检查微信错误码
                    if (body.containsKey("errcode") && ((Number) body.get("errcode")).intValue() != 0) {
                        int errcode = ((Number) body.get("errcode")).intValue();
                        String errmsg = body.containsKey("errmsg") ? (String) body.get("errmsg") : "unknown";
                        log.error("WeChat user info error: errcode={}, errmsg={}", errcode, errmsg);
                        throw new OAuth2AuthenticationException(
                                new OAuth2Error(String.valueOf(errcode), errmsg, null),
                                "WeChat user info error: errcode=" + errcode + ", errmsg=" + errmsg);
                    }

                    // 构建用户属性
                    Map<String, Object> attributes = (Map<String, Object>) body;

                    // 构建权限列表
                    Set<GrantedAuthority> authorities = new HashSet<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

                    return (OAuth2User) new WeChatOAuth2User(attributes, authorities);
                })
                .onErrorMap(OAuth2AuthenticationException.class, e -> e)
                .onErrorMap(e -> new OAuth2AuthenticationException(
                        new OAuth2Error("user_info_error", "Failed to fetch WeChat user info", null),
                        "Failed to fetch WeChat user info", e));
    }
}
