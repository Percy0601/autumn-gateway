package xyz.wewin.autumn.gateway.dashboard.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一个用户算出来的全部权限，作为 {@link LoginUser} 的附属数据挂在
 * Spring Security 的 Authentication 上。
 *
 * <p>不可变对象：构造后不再变更，可以安全地在一次请求内被多处读取。</p>
 *
 * @param superAdmin 是否超级管理员（true 时短路放行所有权限判定）
 * @param authorities 权限标识集合，如 {@code system:user:create}
 * @param urlResources 可访问的 URL 资源
 */
public record UserGrant(boolean superAdmin,
                        Set<String> authorities,
                        List<UrlResource> urlResources) {

    /**
     * 显式构造器（不用紧凑构造器）：
     * 紧凑构造器里不能给字段赋值，必须在尾部写 {@code this.x = ...}。
     * 这里直接做「防御性拷贝 + 置为不可变集合」，保证对象发布后不会被外部改掉。
     */
    public UserGrant(boolean superAdmin, Set<String> authorities, List<UrlResource> urlResources) {
        this.superAdmin = superAdmin;
        this.authorities = authorities == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
        this.urlResources = urlResources == null ? List.of() : List.copyOf(urlResources);
    }

    /** 匿名 / 无权限 */
    public static UserGrant empty() {
        return new UserGrant(false, Collections.emptySet(), Collections.emptyList());
    }

    /**
     * 超级管理员：全权限。
     *
     * <p>方法名不能叫 {@code superAdmin()} —— record 会自动生成同名的字段访问器，
     * 两者签名冲突（此处踩过一次，记下来免得后人再踩）。</p>
     */
    public static UserGrant fullAccess() {
        return new UserGrant(true, Collections.emptySet(), Collections.emptyList());
    }

    public static UserGrant of(Collection<String> authorities, List<UrlResource> resources) {
        return new UserGrant(false, new LinkedHashSet<>(authorities), resources);
    }

    /** 是否拥有某个权限标识；超管恒为 true */
    public boolean has(String authority) {
        return superAdmin || (authority != null && authorities.contains(authority));
    }

    /** record 的 superAdmin 是字段访问器，这里补一个语义命名，调用处更贴近自然语言 */
    public boolean isSuperAdmin() {
        return superAdmin;
    }

    /** 任一命中即可；集合为空时返回 false（默认拒绝） */
    public boolean hasAny(Collection<String> wanted) {
        if (superAdmin) {
            return true;
        }
        if (wanted == null || wanted.isEmpty()) {
            return false;
        }
        for (String w : wanted) {
            if (authorities.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** 只用于日志/排查，避免把整个权限集打进日志 */
    public int size() {
        return authorities.size();
    }
}
