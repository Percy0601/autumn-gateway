package xyz.wewin.autumn.gateway.dashboard.security;

import java.util.Objects;

/**
 * 权限元数据字段的写入保护（D2 / D3 越权修复）。
 *
 * <p>{@code admin} / {@code master} / {@code menu_identification} / {@code path} 这类字段
 * 一旦能被任意登录用户改写，就等于绕过整个 RBAC 模型：把 {@code admin=1} 写成超管、
 * 把角色的 {@code master=1} 写成管理员角色、把已授权菜单的标识改成任意权限码、
 * 把资源的 URL 匹配改宽。而这些字段所在的 create/update 接口按既有产品约定是
 * 「认证即可」，不能给整个接口加权限码（会破坏既有使用方式），因此在字段级收口。</p>
 *
 * <h3>规则</h3>
 * <ul>
 *   <li><b>超管</b>：正常修改，管理后台必须能配置这些字段；</li>
 *   <li><b>非超管 + 值未变</b>：放行 —— 前端编辑表单会把整个实体回传，
 *       不能因为「带了同值字段」就误伤正常保存；</li>
 *   <li><b>非超管 + 值改变</b>：静默忽略，保留库中原值。
 *       静默而不是抛异常，是为了兼容现有表单（提交成功但特权字段不变）。</li>
 * </ul>
 *
 * <p>判定走 {@link Permissions#isSuperAdmin()}，不依赖任何新增权限码，
 * 因此不需要同步修改 basic_menu 种子数据与存量库授权。</p>
 */
public final class ProtectedFields {

    private ProtectedFields() {
    }

    /** 更新已有记录：非超管不得改变该字段的值。incoming 为 null 表示未提交，保持原值。 */
    public static <T> T guard(T existing, T incoming) {
        if (incoming == null || Objects.equals(existing, incoming)) {
            return existing;
        }
        return Permissions.isSuperAdmin() ? incoming : existing;
    }

    /** 新建记录：非超管不得指定特权值，一律回落安全默认值。 */
    public static <T> T guardCreate(T safeDefault, T incoming) {
        if (incoming == null || Objects.equals(safeDefault, incoming)) {
            return safeDefault;
        }
        return Permissions.isSuperAdmin() ? incoming : safeDefault;
    }
}
