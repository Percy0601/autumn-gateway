package xyz.wewin.autumn.gateway.authorization.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

/**
 * 账号信息查询：令牌定制与 userinfo 响应都通过它取用户的 uuid 等对外信息。
 *
 * <p>刻意不从 Authentication 的 principal 里取自定义 UserDetails：
 * 认证成功后 principal 可能被框架规范化，携带的自定义字段会丢失，
 * 以数据库为准最稳。</p>
 */
@Component
public class UserAccountQuery {

    private static final String LOAD_ACCOUNT_SQL = """
            SELECT u.id       AS id,
                   u.uuid     AS uuid,
                   u.username AS username,
                   u.nickname AS nickname,
                   u.email    AS email,
                   u.phone    AS phone
            FROM `user` u
            WHERE u.username = :username
            """;

    private static final String LOAD_ROLES_SQL = """
            SELECT DISTINCT r.code
            FROM user_role ur
            JOIN `role` r ON r.id = ur.role_id
            WHERE ur.user_id = :userId
              AND (ur.expires_at IS NULL OR ur.expires_at > NOW())
              AND (:appId IS NULL OR ur.app_id = :appId)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Long roleAppId;

    public UserAccountQuery(NamedParameterJdbcTemplate jdbcTemplate,
                            @Value("${autumn.authorization-server.role-app-id:}") String roleAppId) {
        this.jdbcTemplate = jdbcTemplate;
        this.roleAppId = StringUtils.hasText(roleAppId) ? Long.valueOf(roleAppId) : null;
    }

    /**
     * 按用户名查询账号信息（含角色）。客户端凭证模式下查不到，返回空。
     */
    public Optional<UserAccount> findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }
        return this.jdbcTemplate.query(LOAD_ACCOUNT_SQL,
                new MapSqlParameterSource("username", username),
                (rs) -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    long userId = rs.getLong("id");
                    String uuid = rs.getString("uuid");
                    UserAccount account = new UserAccount(
                            uuid == null ? String.valueOf(userId) : uuid,
                            rs.getString("username"),
                            rs.getString("nickname") == null ? "" : rs.getString("nickname"),
                            rs.getString("email") == null ? "" : rs.getString("email"),
                            rs.getString("phone") == null ? "" : rs.getString("phone"),
                            loadRoles(userId));
                    return Optional.of(account);
                });
    }

    private List<String> loadRoles(long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("appId", this.roleAppId, Types.BIGINT);
        return this.jdbcTemplate.queryForList(LOAD_ROLES_SQL, params, String.class);
    }

    /**
     * 用户的对外信息
     */
    public record UserAccount(String uuid,
                              String username,
                              String nickname,
                              String email,
                              String phone,
                              List<String> authorities) {
    }
}
