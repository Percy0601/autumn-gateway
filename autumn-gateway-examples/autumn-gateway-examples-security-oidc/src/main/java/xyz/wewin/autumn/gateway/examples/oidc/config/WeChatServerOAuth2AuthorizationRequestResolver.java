package xyz.wewin.autumn.gateway.examples.oidc.config;

import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class WeChatServerOAuth2AuthorizationRequestResolver implements ServerOAuth2AuthorizationRequestResolver {

    private static final String WECHAT_REGISTRATION_ID = "wechat";

    private final DefaultServerOAuth2AuthorizationRequestResolver defaultResolver;

    public WeChatServerOAuth2AuthorizationRequestResolver(ReactiveClientRegistrationRepository clientRegistrationRepository) {
        this.defaultResolver = new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange) {
        return defaultResolver.resolve(exchange)
                .map(this::customizeIfWeChat);
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange, String clientRegistrationId) {
        return defaultResolver.resolve(exchange, clientRegistrationId)
                .map(this::customizeIfWeChat);
    }

    private OAuth2AuthorizationRequest customizeIfWeChat(OAuth2AuthorizationRequest request) {
        if (!WECHAT_REGISTRATION_ID.equals(request.getAttribute("registration_id"))) {
            return request;
        }

        return OAuth2AuthorizationRequest.from(request)
                .parameters(params -> {
                    params.remove("client_id");
                    params.put("appid", request.getClientId());
                    params.remove("code_challenge");
                    params.remove("code_challenge_method");
                })
                .build();
    }
}
