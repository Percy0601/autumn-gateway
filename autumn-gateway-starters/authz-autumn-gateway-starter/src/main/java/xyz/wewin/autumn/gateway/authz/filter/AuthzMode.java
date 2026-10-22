package xyz.wewin.autumn.gateway.authz.filter;

/**
 * 路由鉴权模式：{@code Authz=authenticate,<appCode>} / {@code Authz=authorize,<appCode>}。
 */
public enum AuthzMode {

    /** 只校验 {@code Authorization: Bearer <jwt>}，通过即转发，不查库 */
    AUTHENTICATE("authenticate"),

    /** 校验 JWT 后，再按 appCode 查角色 → 查资源 → Ant 匹配 + method 判定 */
    AUTHORIZE("authorize");

    /** 路由配置里写的字面量（小写，与 Spring Cloud Gateway 短写保持一致） */
    public final String code;

    AuthzMode(String code) {
        this.code = code;
    }

    /** 大小写不敏感解析；无法识别时直接失败，避免把「鉴权」配置错误静默降级成「只认证」。 */
    public static AuthzMode of(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (AuthzMode mode : values()) {
                if (mode.code.equalsIgnoreCase(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                    return mode;
                }
            }
        }
        throw new IllegalArgumentException("不支持的 Authz mode: " + value
                + "，只能使用 authenticate 或 authorize");
    }
}
