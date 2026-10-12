package xyz.wewin.autumn.gateway.dashboard.security;

/**
 * 当前请求上下文，基于 ThreadLocal 保存，供 Service 层读写「谁在操作」。
 *
 * <p>由 {@link JwtAuthenticationFilter} 在请求进入时解析并验签
 * {@code Authorization: Bearer <jwt>} 后写入，请求结束时清理。
 *
 * <p>业务侧典型用法（审计字段 create_user / update_user / op_user_id 均为 NOT NULL）：
 * <pre>{@code
 * role.setCreateUser(RequestContext.currentUserId());
 * role.setUpdateUser(RequestContext.currentUserId());
 * }</pre>
 *
 * <p>未携带 token 或 token 非法时按「匿名」处理，{@link #currentUserId()} 返回 0，
 * 与表结构 {@code DEFAULT 0} 语义一致，避免插入 NULL 触发
 * {@code Column 'create_user' cannot be null}。
 */
public final class RequestContext {

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    /** 匿名用户 id，与表结构 DEFAULT 0 对齐 */
    public static final long ANONYMOUS_USER_ID = 0L;

    private final Long userId;
    private final String username;
    private final String token;

    private RequestContext(Long userId, String username, String token) {
        this.userId = userId;
        this.username = username;
        this.token = token;
    }

    /** 已登录用户上下文 */
    public static RequestContext of(Long userId, String username, String token) {
        return new RequestContext(userId, username, token);
    }

    /** 匿名上下文（无 token 或 token 校验失败） */
    public static RequestContext anonymous() {
        return new RequestContext(ANONYMOUS_USER_ID, null, null);
    }

    public static void set(RequestContext context) {
        HOLDER.set(context);
    }

    public static RequestContext get() {
        return HOLDER.get();
    }

    /** 必须在 finally 中调用，避免线程复用导致串号 */
    public static void clear() {
        HOLDER.remove();
    }

    /** 当前操作人 id；未登录返回 0 */
    public static Long currentUserId() {
        RequestContext context = HOLDER.get();
        return context == null || context.userId == null ? ANONYMOUS_USER_ID : context.userId;
    }

    /** 当前操作人用户名；未登录返回 null */
    public static String currentUsername() {
        RequestContext context = HOLDER.get();
        return context == null ? null : context.username;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    public boolean isAnonymous() {
        return userId == null || ANONYMOUS_USER_ID == userId.longValue();
    }
}
