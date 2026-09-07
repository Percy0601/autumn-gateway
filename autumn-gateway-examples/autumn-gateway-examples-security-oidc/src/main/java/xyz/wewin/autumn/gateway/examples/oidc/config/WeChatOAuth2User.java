package xyz.wewin.autumn.gateway.examples.oidc.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 微信 OAuth2 用户主体
 * <p>
 * 封装微信用户信息接口返回的属性，实现 OAuth2User 接口。
 * 属性映射：
 * <ul>
 *   <li>openid - 用户在当前应用的唯一标识</li>
 *   <li>nickname - 昵称</li>
 *   <li>headimgurl - 头像URL</li>
 *   <li>unionid - 开放平台统一标识（需绑定开放平台应用）</li>
 * </ul>
 */
public class WeChatOAuth2User implements OAuth2User {

    private final Map<String, Object> attributes;
    private final Set<GrantedAuthority> authorities;

    public WeChatOAuth2User(Map<String, Object> attributes) {
        this(attributes, Set.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    public WeChatOAuth2User(Map<String, Object> attributes, Collection<? extends GrantedAuthority> authorities) {
        this.attributes = attributes;
        this.authorities = Set.copyOf(authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return (String) attributes.get("openid");
    }

    public String getNickname() {
        return (String) attributes.get("nickname");
    }

    public String getOpenid() {
        return (String) attributes.get("openid");
    }

    public String getHeadImgUrl() {
        return (String) attributes.get("headimgurl");
    }

    public String getUnionid() {
        return (String) attributes.get("unionid");
    }
}
