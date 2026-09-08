package xyz.wewin.autumn.gateway.authorization.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 登录失败计数与账号锁定（持久化到数据库，支持多实例部署）。
 *
 * <p>规则（主流 ERP / 后台系统的通行做法）：
 * 连续失败达到 maxAttempts 次即锁定 lockMinutes 分钟，锁定期间即便密码正确也拒绝登录；
 * 成功登录后清零；锁定到期自动解锁。</p>
 *
 * <p>两个实现要点：
 * <ul>
 *   <li>锁定判断一律用数据库的 NOW()，避免应用与数据库时区不一致导致误判；</li>
 *   <li>计数与锁定拆成两条 SQL，规避 MySQL "ON DUPLICATE KEY UPDATE" 中
 *       后续表达式读到的是已更新值这一陷阱。</li>
 * </ul>
 * </p>
 */
@Service
public class LoginAttemptService {

    private final JdbcTemplate jdbcTemplate;

    /** 允许的最大连续失败次数 */
    @Value("${autumn.security.login.max-attempts:10}")
    private int maxAttempts;

    /** 触发锁定后的锁定时长（分钟） */
    @Value("${autumn.security.login.lock-minutes:30}")
    private int lockMinutes;

    public LoginAttemptService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 是否处于锁定中（以数据库时间为准）
     */
    public boolean isLocked(String loginKey) {
        return lockedRemainingSeconds(loginKey) > 0;
    }

    /**
     * 锁定剩余秒数，0 表示未锁定
     */
    public int lockedRemainingSeconds(String loginKey) {
        if (loginKey == null || loginKey.isBlank()) {
            return 0;
        }
        Integer seconds = this.jdbcTemplate.query(
                "SELECT TIMESTAMPDIFF(SECOND, NOW(), locked_until) FROM user_login_security " +
                        "WHERE login_key = ? AND locked_until IS NOT NULL",
                (rs) -> rs.next() ? rs.getInt(1) : 0,
                loginKey);
        return (seconds == null || seconds < 0) ? 0 : seconds;
    }

    /**
     * 剩余可尝试次数（已锁定时返回 0）
     */
    public int remainingAttempts(String loginKey) {
        if (isLocked(loginKey)) {
            return 0;
        }
        Integer failed = this.jdbcTemplate.query(
                "SELECT failed_count FROM user_login_security WHERE login_key = ?",
                (rs) -> rs.next() ? rs.getInt("failed_count") : 0,
                loginKey);
        return Math.max(0, maxAttempts - (failed == null ? 0 : failed));
    }

    /**
     * 记录一次失败：计数 +1（锁定中不再累加），达到阈值则锁定
     */
    public void onFailure(String loginKey, String ip) {
        if (loginKey == null || loginKey.isBlank()) {
            return;
        }
        int updated = this.jdbcTemplate.update("""
                        UPDATE user_login_security
                        SET failed_count = failed_count + 1,
                            last_failed_at = NOW(),
                            last_login_ip = ?,
                            updated_at = NOW()
                        WHERE login_key = ?
                          AND (locked_until IS NULL OR locked_until <= NOW())
                        """, ip, loginKey);

        if (updated == 0 && !exists(loginKey)) {
            this.jdbcTemplate.update("""
                            INSERT INTO user_login_security
                                (login_key, failed_count, last_failed_at, last_login_ip, created_at, updated_at)
                            VALUES (?, 1, NOW(), ?, NOW(), NOW())
                            """, loginKey, ip);
        }

        this.jdbcTemplate.update("""
                        UPDATE user_login_security
                        SET locked_until = DATE_ADD(NOW(), INTERVAL ? MINUTE),
                            updated_at = NOW()
                        WHERE login_key = ?
                          AND failed_count >= ?
                          AND (locked_until IS NULL OR locked_until <= NOW())
                        """, lockMinutes, loginKey, maxAttempts);
    }

    /**
     * 登录成功：清零计数并解锁
     */
    public void onSuccess(String loginKey, String ip) {
        if (loginKey == null || loginKey.isBlank()) {
            return;
        }
        this.jdbcTemplate.update("""
                        INSERT INTO user_login_security
                            (login_key, failed_count, last_success_at, last_login_ip, created_at, updated_at)
                        VALUES (?, 0, NOW(), ?, NOW(), NOW())
                        ON DUPLICATE KEY UPDATE
                            failed_count = 0,
                            locked_until = NULL,
                            last_success_at = NOW(),
                            last_login_ip = VALUES(last_login_ip),
                            updated_at = NOW()
                        """, loginKey, ip);
    }

    private boolean exists(String loginKey) {
        Integer count = this.jdbcTemplate.query(
                "SELECT COUNT(1) FROM user_login_security WHERE login_key = ?",
                (rs) -> rs.next() ? rs.getInt(1) : 0,
                loginKey);
        return count != null && count > 0;
    }

    public int getMaxAttempts() {
        return this.maxAttempts;
    }

    public int getLockMinutes() {
        return this.lockMinutes;
    }
}
