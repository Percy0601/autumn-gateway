package xyz.wewin.autumn.gateway.authorization.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信登录配置（微信开放平台 - 网站应用扫码登录）。
 *
 * <p>用户点击"微信登录"后跳转到微信授权页，回调 {@code redirect-uri} 时由
 * {@link xyz.wewin.autumn.gateway.authorization.web.WeChatLoginController} 处理。</p>
 *
 * <p>注意：回调地址 {@code redirect-uri} 必须与微信开放平台后台登记的一致（含协议、端口、路径）。
 * 只有在微信开放平台将公众号/小程序/网站应用绑定到同一开放平台账号后，
 * token 响应里才会返回 unionid（跨应用打通），否则只能用 openid 作为本地标识。</p>
 */
@Component
public class WeChatProperties {

    /** 是否启用微信登录（未配置 appid/secret 时务必保持 false） */
    @Value("${autumn.wechat.enabled:false}")
    private boolean enabled;

    @Value("${autumn.wechat.app-id:}")
    private String appId;

    @Value("${autumn.wechat.app-secret:}")
    private String appSecret;

    /** 微信回调地址（必须与开放平台登记一致） */
    @Value("${autumn.wechat.redirect-uri:http://localhost:9000/login/wechat/callback}")
    private String redirectUri;

    /** 授权作用域：网站应用扫码用 snsapi_login；公众号网页授权用 snsapi_userinfo */
    @Value("${autumn.wechat.scope:snsapi_login}")
    private String scope;

    @Value("${autumn.wechat.authorize-url:https://open.weixin.qq.com/connect/qrconnect}")
    private String authorizeUrl;

    @Value("${autumn.wechat.token-url:https://api.weixin.qq.com/sns/oauth2/access_token}")
    private String tokenUrl;

    @Value("${autumn.wechat.userinfo-url:https://api.weixin.qq.com/sns/userinfo}")
    private String userInfoUrl;

    /** 首次微信登录是否自动创建本地账号；false 时要求先绑定 */
    @Value("${autumn.wechat.auto-create-user:true}")
    private boolean autoCreateUser;

    public boolean isEnabled() {
        return enabled;
    }

    public String getAppId() {
        return appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getScope() {
        return scope;
    }

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public String getUserInfoUrl() {
        return userInfoUrl;
    }

    public boolean isAutoCreateUser() {
        return autoCreateUser;
    }
}
