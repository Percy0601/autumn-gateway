package xyz.wewin.autumn.gateway.examples.consul.config.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.*;

public class ConsulRouteDefinitionLocator implements RouteDefinitionLocator {
    private static final Logger log = LoggerFactory.getLogger(ConsulRouteDefinitionLocator.class);
    private static final String PREFIX = "spring.cloud.gateway.server.webflux.routes";

    private final Environment environment;

    public ConsulRouteDefinitionLocator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        List<RouteDefinition> routes = new ArrayList<>();
        for (int i = 0; i < 1_000_000 ; i++) {
            String id = environment.getProperty(PREFIX + "[" + i + "].id");
            if (id == null) break;

            RouteDefinition route = new RouteDefinition();
            route.setId(id);

            String uri = environment.getProperty(PREFIX + "[" + i + "].uri");
            if (uri != null) {
                try {
                    route.setUri(new URI(uri));
                } catch (java.net.URISyntaxException e) {
                    log.error("Invalid URI for route {}: {}", id, uri, e);
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
            log.debug("Loaded route from Consul: {}", id);
        }
        log.info("Loaded {} routes from Consul KV", routes.size());
        return Flux.fromIterable(routes);
    }
}
