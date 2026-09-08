package xyz.wewin.autumn.gateway.authorization.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 从数据库加载用户与密码（与 dashboard 完全相同的账号契约）。
 *
 * <p>查找入口固定为 {@code (identity_type='password', identifier=用户名)}，
 * 角色来自 user_role + role（可按应用过滤，见 autumn.authorization-server.role-app-id）。</p>
 *
 * <p>这里刻意不依赖 dashboard 模块：授权服务器只与表结构/SQL 契约耦合，可独立部署。</p>
 */
@Service
public class JdbcUserDetailsService implements UserDetailsService {

    /**
     * 认证主查询：认证账户 → 用户
     */
    private static final String LOAD_USER_SQL = """
            SELECT u.id            AS id,
                   u.uuid          AS uuid,
                   u.username      AS username,
                   u.nickname      AS nickname,
                   u.email         AS email,
                   u.phone         AS phone,
                   u.status        AS status,
                   a.credential    AS credential
            FROM user_auth_account a
            JOIN `user` u ON u.id = a.user_id
            WHERE a.identity_type = :identityType
              AND a.identifier = :identifier
            """;

    /**
     * 角色查询：未过期的角色；appId 为空表示不按应用过滤
     */
    private static final String LOAD_ROLES_SQL = """
            SELECT DISTINCT r.code
            FROM user_role ur
            JOIN `role` r ON r.id = ur.role_id
            WHERE ur.user_id = :userId
              AND (ur.expires_at IS NULL OR ur.expires_at > NOW())
              AND (:appId IS NULL OR ur.app_id = :appId)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final LoginAttemptService loginAttemptService;
    private final Long roleAppId;

    public JdbcUserDetailsService(NamedParameterJdbcTemplate jdbcTemplate,
                                  LoginAttemptService loginAttemptService,
                                  @Value("${autumn.authorization-server.role-app-id:}") String roleAppId) {
        this.jdbcTemplate = jdbcTemplate;
        this.loginAttemptService = loginAttemptService;
        this.roleAppId = StringUtils.hasText(roleAppId) ? Long.valueOf(roleAppId) : null;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("identityType", "password")
                .addValue("identifier", username);

        Map<String, Object> row = this.jdbcTemplate.query(LOAD_USER_SQL, params, (rs) -> {
            if (!rs.next()) {
                return null;
            }
            // 用 HashMap 而非 Map.of：uuid / username / credential 等列都可能为 NULL
            Map<String, Object> result = new HashMap<>();
            result.put("id", rs.getObject("id"));
            result.put("uuid", rs.getString("uuid"));
            result.put("username", rs.getString("username"));
            result.put("nickname", rs.getString("nickname") == null ? "" : rs.getString("nickname"));
            result.put("email", rs.getString("email") == null ? "" : rs.getString("email"));
            result.put("phone", rs.getString("phone") == null ? "" : rs.getString("phone"));
            result.put("status", rs.getObject("status"));
            result.put("credential", rs.getString("credential"));
            return result;
        });

        if (row == null) {
            throw new UsernameNotFoundException("账号不存在: " + username);
        }

        String credential = (String) row.get("credential");
        if (!StringUtils.hasText(credential)) {
            // 第三方登录用户未设置密码，不允许走密码登录
            throw new UsernameNotFoundException("账号未设置密码: " + username);
        }

        Long userId = ((Number) row.get("id")).longValue();
        List<GrantedAuthority> authorities = loadAuthorities(userId);

        Integer status = row.get("status") == null ? 1 : ((Number) row.get("status")).intValue();
        String uuid = row.get("uuid") == null ? String.valueOf(userId) : (String) row.get("uuid");
        String loginName = row.get("username") == null ? username : (String) row.get("username");
        // 连续失败超限 → 锁定，由 Spring Security 抛出 LockedException
        boolean locked = this.loginAttemptService.isLocked(loginName);

        return new AutumnUserDetails(uuid,
                loginName,
                credential,
                (String) row.get("nickname"),
                (String) row.get("email"),
                (String) row.get("phone"),
                status == 1,
                !locked,
                authorities);
    }

    private List<GrantedAuthority> loadAuthorities(Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("appId", this.roleAppId, Types.BIGINT);
        List<String> codes = this.jdbcTemplate.queryForList(LOAD_ROLES_SQL, params, String.class);
        return codes.stream()
                .filter(StringUtils::hasText)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
