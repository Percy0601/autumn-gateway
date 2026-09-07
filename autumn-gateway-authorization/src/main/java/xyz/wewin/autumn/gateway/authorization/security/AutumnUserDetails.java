package xyz.wewin.autumn.gateway.authorization.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 扩展 Spring Security 的 UserDetails，携带 OIDC 需要的用户信息。
 *
 * <p>其中 uuid 是用户对外统一标识，会作为 id_token / access_token 的 sub 下发，
 * 不随用户名变更而变化。</p>
 */
public class AutumnUserDetails extends User {

    private final String uuid;
    private final String nickname;
    private final String email;
    private final String phone;

    public AutumnUserDetails(String uuid,
                             String username,
                             String password,
                             String nickname,
                             String email,
                             String phone,
                             boolean enabled,
                             Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.uuid = uuid;
        this.nickname = nickname;
        this.email = email;
        this.phone = phone;
    }

    public String getUuid() {
        return this.uuid;
    }

    public String getNickname() {
        return this.nickname;
    }

    public String getEmail() {
        return this.email;
    }

    public String getPhone() {
        return this.phone;
    }
}
