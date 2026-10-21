package xyz.wewin.autumn.gateway.authn.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关统一认证配置（前缀 {@code autumn.authn}）。
 *
 * <p>网关作为 OAuth2 <b>客户端</b>：浏览器只与网关打交道，网关负责把未认证请求
 * 送进授权服务器（{@code autumn-gateway-authorization}），拿到令牌后放进服务端会话，
 * 再由 {@code TokenRelay} 过滤器把 access_token 注入转发给下游服务的请求头。</p>
 */
@ConfigurationProperties(prefix = "autumn.authn")
public class AuthnGatewayProperties {

    /** 总开关：false 时不注册任何认证相关 Bean（网关退化为纯路由） */
    private boolean enabled = true;

    /** 客户端注册 ID：决定登录入口 {@code /oauth2/authorization/{registration-id}} */
    private String registrationId = "web-client";

    private String clientId = "web-client";

    private String clientSecret = "secret";

    /** 授权服务器地址（issuer），各协议端点按 OIDC 约定拼接 */
    private String issuerUri = "http://localhost:9000";

    /** 逗号分隔的 scope；含 openid 时走 OIDC（会拿到 id_token） */
    private String scope = "openid,profile";

    /** 回调地址模板；{baseUrl} 会在运行时按网关自身地址展开 */
    private String redirectUriTemplate = "{baseUrl}/login/oauth2/code/{registrationId}";

    /** 登出路径 */
    private String logoutUrl = "/logout";

    /** 登出后跳转的地址 */
    private String logoutSuccessUrl = "/";

    /** 匿名放行的路径（Ant 风格）。健康检查必须放行，否则探活会被 302 到登录页 */
    private List<String> permitAll = new ArrayList<>(List.of("/actuator/health", "/actuator/health/**"));

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRegistrationId() {
        return this.registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getClientId() {
        return this.clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return this.clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getIssuerUri() {
        return this.issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getScope() {
        return this.scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getRedirectUriTemplate() {
        return this.redirectUriTemplate;
    }

    public void setRedirectUriTemplate(String redirectUriTemplate) {
        this.redirectUriTemplate = redirectUriTemplate;
    }

    public String getLogoutUrl() {
        return this.logoutUrl;
    }

    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
    }

    public String getLogoutSuccessUrl() {
        return this.logoutSuccessUrl;
    }

    public void setLogoutSuccessUrl(String logoutSuccessUrl) {
        this.logoutSuccessUrl = logoutSuccessUrl;
    }

    public List<String> getPermitAll() {
        return this.permitAll;
    }

    public void setPermitAll(List<String> permitAll) {
        this.permitAll = permitAll;
    }

    /** {@code "openid,profile"} → {@code ["openid","profile"]} */
    public List<String> resolvedScopes() {
        List<String> scopes = new ArrayList<>();
        if (this.scope != null && !this.scope.isBlank()) {
            for (String part : this.scope.split(",")) {
                if (!part.isBlank()) {
                    scopes.add(part.trim());
                }
            }
        }
        return scopes;
    }
}
