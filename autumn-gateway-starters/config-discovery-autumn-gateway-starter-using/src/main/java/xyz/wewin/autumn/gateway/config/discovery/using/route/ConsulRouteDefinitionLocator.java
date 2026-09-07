package xyz.wewin.autumn.gateway.config.discovery.using.route;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

/**
 * 从 Environment（含 Consul 导入的配置）读取网关路由定义。
 * 路由 key 放 Consul KV 后，配合 EnvironmentChangeEvent 监听即可实现「路由热更新」。
 */
@Component
public class ConsulRouteDefinitionLocator implements RouteDefinitionLocator {

    private static final String PREFIX = "spring.cloud.gateway.server.webflux.routes";

    private final Environment environment;

    public ConsulRouteDefinitionLocator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        List<RouteDefinition> routes = new ArrayList<>();
        for (int i = 0; i < 1_000_000; i++) {
            String id = environment.getProperty(PREFIX + "[" + i + "].id");
            if (id == null) {
                break;
            }
            RouteDefinition route = new RouteDefinition();
            route.setId(id);
            String uri = environment.getProperty(PREFIX + "[" + i + "].uri");
            if (uri != null) {
                try {
                    route.setUri(URI.create(uri));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("路由 " + id + " 的 uri 非法: " + uri, e);
                }
            }
            String predicate = environment.getProperty(PREFIX + "[" + i + "].predicates[0]");
            if (predicate != null) {
                route.getPredicates().add(new PredicateDefinition(predicate));
            }
            String filter = environment.getProperty(PREFIX + "[" + i + "].filters[0]");
            if (filter != null) {
                route.getFilters().add(new FilterDefinition(filter));
            }
            routes.add(route);
        }
        return Flux.fromIterable(routes);
    }
}
