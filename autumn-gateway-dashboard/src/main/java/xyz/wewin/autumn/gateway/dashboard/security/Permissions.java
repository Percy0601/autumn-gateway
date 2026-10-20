package xyz.wewin.autumn.gateway.dashboard.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 按钮级权限判定的编程式入口。
 *
 * <p>两种用法，任选其一：</p>
 * <pre>{@code
 * // 1) 注解式（推荐，声明在 Controller/Service 方法上，意图一目了然）
 * @PreAuthorize("hasAuthority('system:user:create')")
 * public Result<Long> create(@RequestBody BasicUser user) { ... }
 *
 * // 2) 编程式（条件复杂时用，比如「自己可以改自己，或拥有 update 权限」）
 * if (!Permissions.has("system:user:update") && !isSelf(id)) {
 *     throw new AccessDeniedException("无权修改他人信息");
 * }
 * }</pre>
 *
 * <p><b>为什么要有编程式入口：</b>注解拿不到方法参数之外的条件，
 * 也拿不到「当前用户 id 与目标记录 owner 是否一致」这类判断。
 * 有一个统一入口，比到处 {@code SecurityContextHolder.getContext()...} 更可控。</p>
 */
@Component("permissions")
public class Permissions {

    /** 是否拥有某权限标识；未登录或非 LoginUser 恒 false */
    public static boolean has(String authority) {
        LoginUser user = current();
        return user != null && user.hasPermission(authority);
    }

    /** 任一命中即 true */
    public static boolean hasAny(Collection<String> authorities) {
        LoginUser user = current();
        return user != null && user.grant().hasAny(authorities);
    }

    /** 全部命中才 true */
    public static boolean hasAll(Collection<String> authorities) {
        LoginUser user = current();
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }
        for (String a : authorities) {
            if (!user.hasPermission(a)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSuperAdmin() {
        LoginUser user = current();
        return user != null && user.isSuperAdmin();
    }

    /** 当前登录用户 id；未登录返回 null（区别于 RequestContext 的 0） */
    public static Long currentUserId() {
        LoginUser user = current();
        return user == null ? null : user.getUserId();
    }

    /** 当前用户权限标识清单，用于 /api/currentUser 下发 */
    public static List<String> currentAuthorities() {
        LoginUser user = current();
        if (user == null) {
            return List.of();
        }
        if (user.isSuperAdmin()) {
            // 超管不逐条列举，前端见 "*" 即视为全放行
            return List.of("*");
        }
        return List.copyOf(user.permissions());
    }

    private static LoginUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof LoginUser user ? user : null;
    }
}
