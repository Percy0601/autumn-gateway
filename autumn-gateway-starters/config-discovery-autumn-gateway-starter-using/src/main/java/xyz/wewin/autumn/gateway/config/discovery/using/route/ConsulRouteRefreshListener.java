package xyz.wewin.autumn.gateway.config.discovery.using.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consul 配置变更（含路由键）→ 触发网关路由刷新。
 */
@Component
public class ConsulRouteRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(ConsulRouteRefreshListener.class);

    private final ApplicationEventPublisher publisher;

    public ConsulRouteRefreshListener(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        log.info("Consul 配置变更 keys={}, 刷新网关路由", event.getKeys());
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }
}
