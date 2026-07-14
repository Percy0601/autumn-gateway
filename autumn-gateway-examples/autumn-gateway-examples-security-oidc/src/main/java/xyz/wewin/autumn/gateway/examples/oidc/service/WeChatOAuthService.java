package xyz.wewin.autumn.gateway.examples.oidc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import xyz.wewin.autumn.gateway.examples.oidc.config.WeChatProperties;
import xyz.wewin.autumn.gateway.examples.oidc.dto.WeChatUserInfo;

import java.util.Map;

/**
 * 微信 OAuth2 服务
 * 负责与微信 API 交互：code 换 token、获取用户信息
 *
 * @author: baoxin.zhao
 * @date: 2026/7/14
 */
@Service
public class WeChatOAuthService {

    private static final Logger log = LoggerFactory.getLogger(WeChatOAuthService.class);

    private final WeChatProperties weChatProperties;
    private final WebClient webClient;

    public WeChatOAuthService(WeChatProperties weChatProperties, WebClient.Builder webClientBuilder) {
        this.weChatProperties = weChatProperties;
        this.webClient = webClientBuilder.build();
    }

    /**
     * 用授权 code 换取 access_token 和 openid
     *
     * @param code 微信授权回调返回的 code
     * @return 包含 access_token, openid, unionid 等的 Map
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getAccessToken(String code) {
        String url = UriComponentsBuilder
                .fromUriString(weChatProperties.getAccessTokenUrl())
                .queryParam("appid", weChatProperties.getAppId())
                .queryParam("secret", weChatProperties.getAppSecret())
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(resp -> {
                    if (resp.containsKey("errcode")) {
                        log.error("微信获取 access_token 失败: errcode={}, errmsg={}",
                                resp.get("errcode"), resp.get("errmsg"));
                    }
                })
                .map(m -> (Map<String, Object>) m);
    }

    /**
     * 用 access_token 和 openid 获取微信用户信息
     *
     * @param accessToken 微信 access_token
     * @param openId      微信用户 openid
     * @return 微信用户信息
     */
    public Mono<WeChatUserInfo> getUserInfo(String accessToken, String openId) {
        String url = UriComponentsBuilder
                .fromUriString(weChatProperties.getUserInfoUrl())
                .queryParam("access_token", accessToken)
                .queryParam("openid", openId)
                .queryParam("lang", "zh_CN")
                .toUriString();

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(WeChatUserInfo.class)
                .doOnNext(info -> log.info("获取微信用户信息: {}", info));
    }

    /**
     * 构建微信授权页 URL（前端跳转用）
     *
     * @param state 防 CSRF 随机字符串
     * @return 微信授权页完整 URL
     */
    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder
                .fromUriString(weChatProperties.getAuthorizeUrl())
                .queryParam("appid", weChatProperties.getAppId())
                .queryParam("redirect_uri", weChatProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "snsapi_login")
                .queryParam("state", state)
                .fragment("wechat_redirect")
                .toUriString();
    }
}
