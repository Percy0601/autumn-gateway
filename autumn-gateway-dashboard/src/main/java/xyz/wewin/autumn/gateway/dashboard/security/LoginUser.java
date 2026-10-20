package xyz.wewin.autumn.gateway.dashboard.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 认证主体：JWT 解析出来的当前用户 + 其权限。
 *
 * <p>放在 {@code Authentication#getPrincipal()} 里，业务侧可以这样取：</p>
 * <pre>{@code
 * @GetMapping("/api/currentUser")
 * public Object me(@AuthenticationPrincipal LoginUser me) {
 *     return me.permissions();   // 下发前端 v-auth 用的权限标识清单
 * }
 * }</pre>
 *
 * <p><b>为什么不直接用 userId 当 principal：</b>
 * 原实现把 {@code Long userId} 塞进 principal，业务侧取用户信息还得再查一次库；
 * 换成对象后，权限、昵称都随请求带着走，也是 Spring Security 的惯用法。</p>
 */
public class LoginUser implements UserDetails {

    /** 超管通配权限标识；仅作为「全放行」的显式信号，判定请以 {@link UserGrant#has} 为准 */
    public static final String WILDCARD_AUTHORITY = "*";

    private final Long userId;
    private final String username;
    private final String token;
    private final UserGrant grant;
    private final List<GrantedAuthority> authorities;

    public LoginUser(Long userId, String username, String token, UserGrant grant) {
        this.userId = userId;
        this.username = username;
        this.token = token;
        this.grant = grant == null ? UserGrant.empty() : grant;
        // 超管：库里全部权限标识 + 一个 "*" 通配标记。
        //
        // 带全部标识的原因：@PreAuthorize("hasAuthority('x')") 是字符串精确比对，
        // 只认 authorities，不认我们自定义的 "*"。只给 "*" 的话超管会被自己的系统拒之门外。
        // 保留 "*" 是为了给前端 v-auth 与自定义表达式留一个「全放行」的显式信号。
        //
        // 普通用户：authorities 即 basic_menu.menu_identification 集合。
        // 两条路径最终都以 UserGrant#has 为准（见 UrlAuthorizationManager 与 Permissions）。
        this.authorities = buildAuthorities(this.grant);
    }

    private static List<GrantedAuthority> buildAuthorities(UserGrant grant) {
        if (grant.isSuperAdmin()) {
            List<GrantedAuthority> list = new java.util.ArrayList<>(grant.authorities().size() + 1);
            grant.authorities().forEach(a -> list.add(new SimpleGrantedAuthority(a)));
            list.add(new SimpleGrantedAuthority(WILDCARD_AUTHORITY));
            return List.copyOf(list);
        }
        return grant.authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public Long getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public UserGrant grant() {
        return grant;
    }

    /** 是否超管 */
    public boolean isSuperAdmin() {
        return grant.isSuperAdmin();
    }

    /** 权限标识清单，供前端 v-auth 下发 */
    public Collection<String> permissions() {
        return grant.authorities();
    }

    /** 是否拥有某权限标识（超管恒 true） */
    public boolean hasPermission(String authority) {
        return grant.has(authority);
    }

    // ---------- UserDetails ----------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * 本项目走 JWT 无状态认证，密码字段不参与任何校验，
     * 固定返回空串以满足接口契约（切勿在此返回真实哈希）。
     */
    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        // 不含 token，避免日志泄露凭据
        return "LoginUser{id=" + userId + ", username='" + username
                + "', superAdmin=" + grant.isSuperAdmin() + '}';
    }
}
