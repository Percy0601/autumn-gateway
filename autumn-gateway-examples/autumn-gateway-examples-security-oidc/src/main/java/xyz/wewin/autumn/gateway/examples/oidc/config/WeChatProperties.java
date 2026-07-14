package xyz.wewin.autumn.gateway.examples.oidc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信开放平台 OAuth2 配置
 * 
 * @author: baoxin.zhao
 * @date: 2026/7/14
 */
@Component
@ConfigurationProperties(prefix = "wechat.oauth2")
public class WeChatProperties {

    /**
     * 微信开放平台应用 AppID
     */
    private String appId;

    /**
     * 微信开放平台应用 AppSecret
     */
    private String appSecret;

    /**
     * 微信授权回调地址（前端跳转用）
     */
    private String redirectUri;

    /**
     * 微信获取 access_token 接口
     */
    private String accessTokenUrl = "https://api.weixin.qq.com/sns/oauth2/access_token";

    /**
     * 微信获取用户信息接口
     */
    private String userInfoUrl = "https://api.weixin.qq.com/sns/userinfo";

    /**
     * 微信授权页地址（前端跳转用）
     */
    private String authorizeUrl = "https://open.weixin.qq.com/connect/qrconnect";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getAccessTokenUrl() {
        return accessTokenUrl;
    }

    public void setAccessTokenUrl(String accessTokenUrl) {
        this.accessTokenUrl = accessTokenUrl;
    }

    public String getUserInfoUrl() {
        return userInfoUrl;
    }

    public void setUserInfoUrl(String userInfoUrl) {
        this.userInfoUrl = userInfoUrl;
    }

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }

    public void setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }
}
