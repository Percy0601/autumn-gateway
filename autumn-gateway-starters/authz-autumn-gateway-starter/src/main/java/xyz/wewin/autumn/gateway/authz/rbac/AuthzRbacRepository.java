package xyz.wewin.autumn.gateway.authz.rbac;

import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.wewin.autumn.gateway.authz.autoconfigure.AuthzGatewayProperties;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * RBAC 数据访问：JDBC {@link DataSource} + 原生 {@link Connection}/{@link PreparedStatement}。
 *
 * <h3>为什么不用 JdbcTemplate / R2DBC</h3>
 * <ul>
 *   <li>只用原生 JDBC，starter 对数据库、对 Spring 的 DAO 抽象都是零假设，
 *       部署方换驱动、换连接池都不影响这里；</li>
 *   <li>语句全部来自 {@link AuthzGatewayProperties.Sql} 配置，没有预编译的实体映射。</li>
 * </ul>
 *
 * <h3>线程模型（关键）</h3>
 * JDBC 是阻塞 API，而网关跑在 Reactor event loop 上。这里每个查询都用
 * {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())} 包一层，
 * 保证连接获取、执行、取结果集全都发生在 boundedElastic 的线程里，绝不阻塞 event loop。
 * boundedElastic 有上限（默认 CPU×10）且有队列，数据库抖动时会被动限流而不是拖垮网关。
 */
public final class AuthzRbacRepository {

    private final DataSource dataSource;
    private final AuthzGatewayProperties.Sql sql;
    private final int maxLoginAttempts;

    public AuthzRbacRepository(DataSource dataSource, AuthzGatewayProperties properties) {
        this.dataSource = dataSource;
        this.sql = properties.getSql();
        this.maxLoginAttempts = properties.getMaxLoginAttempts();
    }

    /** 按登录名查用户；查不到返回 empty。用户不存在时不写任何更新，调用方自行决定。 */
    public Mono<AuthzUserRecord> findUser(String username) {
        return Mono.fromCallable(() -> queryUser(username))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 用户在指定应用下的有效角色。 */
    public Mono<List<AuthzRole>> findRoles(long userId, String appCode) {
        return Mono.fromCallable(() -> queryRoles(userId, appCode))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 单个角色在该应用下被授权的资源（type=2 直授 + type=1 菜单带出）。 */
    public Mono<List<AuthzResource>> findResources(String appCode, long roleId) {
        return Mono.fromCallable(() -> queryResources(appCode, roleId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 登录失败：扣减剩余次数，归零则把 status 置 0。参数 nextRemaining, nextStatus, userId。 */
    public Mono<Void> recordFailure(long userId, int nextRemaining, int nextStatus) {
        return Mono.fromCallable(() -> {
                    executeUpdate(this.sql.getLoginFailureSql(), nextRemaining, nextStatus, userId);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /** 登录成功：恢复重试次数并刷新 last_login_at。参数 maxAttempts, userId。 */
    public Mono<Void> recordSuccess(long userId, int maxAttempts) {
        return Mono.fromCallable(() -> {
                    executeUpdate(this.sql.getLoginSuccessSql(), maxAttempts, userId);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private AuthzUserRecord queryUser(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(this.sql.getUserQuery())) {
            statement.setString(1, username.trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int remaining = rs.getInt("login_error_count");
                if (rs.wasNull()) {
                    remaining = this.maxLoginAttempts;
                }
                return new AuthzUserRecord(
                        rs.getLong("user_id"),
                        rs.getString("subject"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getInt("status"),
                        remaining,
                        rs.getInt("admin") == 1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("查询登录用户失败", ex);
        }
    }

    private List<AuthzRole> queryRoles(long userId, String appCode) {
        List<AuthzRole> roles = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(this.sql.getRolesQuery())) {
            statement.setLong(1, userId);
            statement.setString(2, appCode);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    roles.add(new AuthzRole(rs.getLong("role_id"),
                            rs.getString("role_code"),
                            rs.getInt("role_master") == 1));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("查询用户角色失败", ex);
        }
        return List.copyOf(roles);
    }

    private List<AuthzResource> queryResources(String appCode, long roleId) {
        List<AuthzResource> resources = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(this.sql.getResourceQuery())) {
            statement.setString(1, appCode);
            statement.setLong(2, roleId);
            statement.setLong(3, roleId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    resources.add(new AuthzResource(rs.getString("resource_path"),
                            rs.getString("http_method"),
                            rs.getString("match_type")));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("查询角色资源失败", ex);
        }
        return List.copyOf(resources);
    }

    private void executeUpdate(String statement, Object... params) {
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(statement)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            int affected = ps.executeUpdate();
            if (affected != 1) {
                throw new IllegalStateException("更新登录状态失败：预期影响 1 行，实际影响 " + affected + " 行");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("更新登录状态失败", ex);
        }
    }
}
