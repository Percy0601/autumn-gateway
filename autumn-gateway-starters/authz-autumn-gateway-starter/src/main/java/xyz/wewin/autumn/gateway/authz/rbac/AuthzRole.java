package xyz.wewin.autumn.gateway.authz.rbac;

/**
 * 用户在指定应用下的角色（对应 {@code autumn.authz.sql.roles-query} 的固定别名）。
 *
 * @param id      role_id
 * @param code    role_code
 * @param master  role_master：basic_role.master = 1 视为管理员角色，直通不查资源
 */
public record AuthzRole(long id, String code, boolean master) {
}
