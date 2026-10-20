package xyz.wewin.autumn.gateway.dashboard.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 权限缓存失效的轻量机制。
 *
 * <p>权限缓存默认 60 秒过期。如果只靠 TTL，运维在页面上「给某人加了角色」之后
 * 最多要等 1 分钟才生效——这在排查权限问题时很折磨人，也会让人误以为
 * 「授权没保存成功」而反复点击。所以授权变更后要主动清缓存。</p>
 *
 * <h3>为什么用 Spring 事件而不是 AOP 切面</h3>
 * 切面需要 {@code spring-boot-starter-aspectj}，为一个「清 Map」的动作
 * 引入字节码织入依赖不划算（Boot 4 已把它改名，也容易踩版本坑）。
 * Spring 的 {@link ApplicationEventPublisher} 是核心容器自带的，
 * 零额外依赖，语义也更清楚：<b>「发生了授权变更」是领域事件，不是横切关注点。</b>
 *
 * <h3>怎么用</h3>
 * <p>在 Service 的授权写方法末尾加一行：</p>
 * <pre>{@code
 * permissionCacheInvalidator.publish();
 * }</pre>
 *
 * <p>不使用 AOP 的通配切点还有个好处：不会把 {@code setTitle} 这类普通实体
 * setter 误判为授权变更，避免「每次字段赋值都清一次全局缓存」导致缓存形同虚设。</p>
 */
@Component
public class PermissionCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheInvalidator.class);

    private final PermissionService permissionService;
    private final ApplicationEventPublisher publisher;

    public PermissionCacheInvalidator(PermissionService permissionService,
                                      ApplicationEventPublisher publisher) {
        this.permissionService = permissionService;
        this.publisher = publisher;
    }

    /**
     * 发布「授权已变更」事件。调用方不需要关心谁在监听、怎么清。
     *
     * <p>为什么清空全量而不是精确清某个用户：
     * 角色变更会影响所有持有该角色的用户，精确反查要多一次关联查询，
     * 还可能漏算「用户组层级继承」这条链路。清空内存 Map 的成本
     * 只是下一次请求多几条 SQL，可以接受。</p>
     */
    public void publish() {
        publisher.publishEvent(new GrantChangedEvent());
    }

    /** 授权变更事件；无字段，仅作信号用 */
    public record GrantChangedEvent() {
    }

    @EventListener
    public void onGrantChanged(GrantChangedEvent event) {
        permissionService.evictAll();
        log.debug("收到授权变更事件，已清空权限缓存");
    }

    /**
     * 应用启动完成后预热一次，让第一条真实请求不必承担建缓存的开销。
     *
     * <p>顺手做一次「配置自检」：如果初始化 SQL 没跑，
     * 这里会明确报出来，比等到用户登录后看到 403 满世界找原因要好。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        permissionService.evictAll();
        long menus = permissionService.countActiveMenus();
        if (menus == 0) {
            log.warn("basic_menu 中没有启用状态的菜单，RBAC 初始化脚本可能未执行。"
                    + "请依次执行 db/rbac/V1__rbac_schema_*.sql 与 V2__rbac_init_data_*.sql");
        } else {
            log.info("RBAC 就绪自检通过：启用菜单 {} 条", menus);
        }
    }
}
