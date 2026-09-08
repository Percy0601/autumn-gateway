package xyz.wewin.autumn.gateway.authorization.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 把微信身份（unionid/openid）映射为本地用户：
 * 已绑定则直接返回；未绑定且允许自动注册则新建本地账号 + 认证账户。
 *
 * <p>落库契约与 dashboard 完全一致：{@code user_auth_account.identity_type='wechat'}、
 * {@code issuer=微信appid}、{@code identifier=unionid/openid}、
 * {@code union_id=unionid}。</p>
 */
@Service
public class WeChatAccountService {

    private static final String LOAD_BY_WECHAT_SQL = """
            SELECT u.id, u.uuid, u.username, u.nickname, u.email, u.phone
            FROM user_auth_account a
            JOIN `user` u ON u.id = a.user_id
            WHERE a.identity_type = 'wechat'
              AND a.issuer = ?
              AND a.identifier = ?
            """;

    private static final String LOAD_USER_BY_ID_SQL = """
            SELECT id, uuid, username, nickname, email, phone
            FROM `user` WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public WeChatAccountService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 解析本地账号：查找 →（未找到且允许）自动创建 → 否则抛异常。
     */
    public LocalUser resolveLocalUser(WeChatService.WeChatUserInfo info,
                                      String appId,
                                      boolean autoCreate) {
        String principalId = WeChatService.principalId(info);
        LocalUser existing = findExisting(appId, principalId);
        if (existing != null) {
            return existing;
        }
        if (!autoCreate) {
            throw new IllegalStateException("该微信账号尚未绑定，请先用密码登录后在个人中心绑定");
        }
        return createLocalUser(info, appId, principalId);
    }

    private LocalUser findExisting(String appId, String principalId) {
        return this.jdbcTemplate.query(LOAD_BY_WECHAT_SQL, (rs) -> {
            if (!rs.next()) {
                return null;
            }
            return toLocalUser(rs);
        }, appId, principalId);
    }

    private LocalUser createLocalUser(WeChatService.WeChatUserInfo info,
                                      String appId,
                                      String principalId) {
        String uuid = UUID.randomUUID().toString();
        String username = generateUsername();
        Long userId = insertUser(uuid, username, info.nickname(), info.headimgurl());
        this.jdbcTemplate.update(
                "INSERT INTO user_auth_account "
                        + "(user_id, identity_type, identifier, issuer, union_id, verified, created_at, updated_at) "
                        + "VALUES (?, 'wechat', ?, ?, ?, 1, NOW(), NOW())",
                userId, principalId, appId, info.unionid());
        return new LocalUser(userId, uuid, username, info.nickname(), "", "");
    }

    private Long insertUser(String uuid, String username, String nickname, String avatar) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update((java.sql.Connection con) -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO `user` (uuid, username, nickname, avatar, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 1, NOW(), NOW())",
                    new String[]{"id"});
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setString(3, nickname);
            ps.setString(4, avatar);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    /**
     * 生成唯一登录名（本地占位用，微信用户实际以 openid 登录，不用于密码登录）。
     */
    private String generateUsername() {
        for (int i = 0; i < 10; i++) {
            String candidate = "wx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            Integer count = this.jdbcTemplate.query(
                    "SELECT COUNT(1) FROM `user` WHERE username = ?",
                    (rs) -> rs.next() ? rs.getInt(1) : 0, candidate);
            if (count != null && count == 0) {
                return candidate;
            }
        }
        return "wx_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 按本地用户 id 读取精简视图（供构造 AutumnUserDetails 与下发 OIDC 声明）。
     */
    public LocalUser loadById(Long userId) {
        return this.jdbcTemplate.query(LOAD_USER_BY_ID_SQL, (rs) -> {
            if (!rs.next()) {
                return null;
            }
            return toLocalUser(rs);
        }, userId);
    }

    public void touchLogin(Long userId) {
        this.jdbcTemplate.update(
                "UPDATE `user` SET last_login_at = NOW(), updated_at = NOW() WHERE id = ?", userId);
    }

    private LocalUser toLocalUser(ResultSet rs) throws SQLException {
        return new LocalUser(
                rs.getLong("id"),
                rs.getString("uuid"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("email"),
                rs.getString("phone"));
    }

    /**
     * 本地用户精简视图。
     */
    public record LocalUser(Long id, String uuid, String username, String nickname, String email, String phone) {
    }
}
