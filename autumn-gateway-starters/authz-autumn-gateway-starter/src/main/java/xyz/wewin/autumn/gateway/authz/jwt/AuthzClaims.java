package xyz.wewin.autumn.gateway.authz.jwt;

import java.util.List;

/**
 * 解析成功的令牌载荷（只保留鉴权需要的字段）。
 *
 * @param subject     sub：优先取 basic_user.uuid，没有 uuid 时回退 user_id
 * @param userId      basic_user.id（字符串形式，便于直接写进响应头）
 * @param username    登录名
 * @param authorities 角色编码集合
 * @param admin       是否管理员（basic_user.admin = 1），管理员直通不查资源
 */
public record AuthzClaims(String subject, String userId, String username, List<String> authorities, boolean admin) {

    public AuthzClaims {
        authorities = (authorities == null) ? List.of() : List.copyOf(authorities);
    }

    /** 逗号分隔的角色串，用于写入 {@code X-Autumn-Roles} */
    public String rolesHeader() {
        return String.join(",", this.authorities);
    }
}
