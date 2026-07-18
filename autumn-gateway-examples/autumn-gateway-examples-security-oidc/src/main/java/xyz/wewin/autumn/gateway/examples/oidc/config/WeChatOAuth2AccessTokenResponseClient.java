package xyz.wewin.autumn.gateway.examples.oidc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WeChatOAuth2AccessTokenResponseClient
        implements ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private static final Logger log = LoggerFactory.getLogger(WeChatOAuth2AccessTokenResponseClient.class);
    private final WebClient webClient = WebClient.builder().build();

    @Override
    @SuppressWarnings("unchecked")
    public Mono<OAuth2AccessTokenResponse> getTokenResponse(OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
        String code = authorizationGrantRequest.getAuthorizationExchange()
                .getAuthorizationResponse().getCode();

        String registrationId = authorizationGrantRequest.getClientRegistration().getRegistrationId();

        String tokenUri = authorizationGrantRequest.getClientRegistration()
                .getProviderDetails().getTokenUri();

        String uri = tokenUri
                + "?appid=" + authorizationGrantRequest.getClientRegistration().getClientId()
                + "&secret=" + authorizationGrantRequest.getClientRegistration().getClientSecret()
                + "&code=" + code
                + "&grant_type=authorization_code";

        log.info("Requesting WeChat access token, registrationId={}", registrationId);

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> {
                    try {
                        if (body.containsKey("errcode") && ((Number) body.get("errcode")).intValue() != 0) {
                            String errmsg = body.containsKey("errmsg") ? (String) body.get("errmsg") : "unknown";
                            int errcode = ((Number) body.get("errcode")).intValue();
                            log.error("WeChat token error: errcode={}, errmsg={}", errcode, errmsg);
                            throw new OAuth2AuthenticationException(
                                    new OAuth2Error(String.valueOf(errcode), errmsg, null),
                                    "WeChat access token error: errcode=" + errcode + ", errmsg=" + errmsg);
                        }

                        if (!body.containsKey("access_token")) {
                            throw new OAuth2AuthenticationException(
                                    new OAuth2Error("missing_access_token", "Missing access_token in response", null),
                                    "Missing access_token in WeChat response");
                        }

                        String accessToken = (String) body.get("access_token");
                        int expiresIn = ((Number) body.get("expires_in")).intValue();

                        OAuth2AccessTokenResponse.Builder builder = OAuth2AccessTokenResponse.withToken(accessToken)
                                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                                .expiresIn(expiresIn);

                        if (body.containsKey("refresh_token") && body.get("refresh_token") != null) {
                            builder.refreshToken((String) body.get("refresh_token"));
                        }

                        Set<String> scopes = body.containsKey("scope")
                                ? Set.of(((String) body.get("scope")).split(","))
                                : Collections.emptySet();
                        builder.scopes(scopes);

                        // 将 openid / unionid 存入 additionalParameters，供后续 WeChatReactiveOAuth2UserService 使用
                        Map<String, Object> additionalParams = new HashMap<>();
                        if (body.containsKey("openid") && body.get("openid") != null) {
                            additionalParams.put("openid", body.get("openid"));
                        }
                        if (body.containsKey("unionid") && body.get("unionid") != null) {
                            additionalParams.put("unionid", body.get("unionid"));
                        }
                        if (!additionalParams.isEmpty()) {
                            builder.additionalParameters(additionalParams);
                        }

                        return builder.build();
                    } catch (OAuth2AuthenticationException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new OAuth2AuthenticationException(
                                new OAuth2Error("parse_error", "Failed to parse WeChat token response", null),
                                "Failed to parse WeChat token response", e);
                    }
                });
    }
}
