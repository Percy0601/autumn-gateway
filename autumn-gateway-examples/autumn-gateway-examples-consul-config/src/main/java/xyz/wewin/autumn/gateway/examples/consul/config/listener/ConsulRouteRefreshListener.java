package xyz.wewin.autumn.gateway.examples.consul.config.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConsulRouteRefreshListener {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ApplicationEventPublisher publisher;

    public ConsulRouteRefreshListener(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onConfigChange(EnvironmentChangeEvent event) {
        log.info("Consul Config changed keys: {}, refreshing routes...", event.getKeys());
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }
}
