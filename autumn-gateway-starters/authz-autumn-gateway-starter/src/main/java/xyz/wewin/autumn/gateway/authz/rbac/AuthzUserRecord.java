package xyz.wewin.autumn.gateway.authz.rbac;

/**
 * 登录用户行（对应 {@code autumn.authz.sql.user-query} 的固定别名）。
 *
 * @param userId          user_id：basic_user.id
 * @param subject         subject：basic_user.uuid，可作为 JWT 的 sub
 * @param username        username：登录名
 * @param passwordHash    password_hash：BCrypt 哈希
 * @param status          status：1=正常 0=禁用
 * @param loginErrorCount login_error_count：剩余重试次数
 * @param admin           admin：1=管理员
 */
public record AuthzUserRecord(long userId, String subject, String username,
                              String passwordHash, int status, int loginErrorCount, boolean admin) {
}
