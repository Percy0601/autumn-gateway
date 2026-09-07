package xyz.wewin.autumn.gateway.examples.opentelemetry.filter;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

//@Component
public class GateTraceGlobalFilter implements GlobalFilter, Ordered {

    private final ObservationRegistry observationRegistry;

    public GateTraceGlobalFilter(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .contextWrite(ctx -> {
                    Observation.createNotStarted("gateway.route", observationRegistry)
                            .lowCardinalityKeyValue("route.id",
                                    exchange.getAttributeOrDefault("org.springframework.cloud.gateway.route.id", "unknown"))
                            .lowCardinalityKeyValue("http.path", exchange.getRequest().getPath().value())
                            .observe(() -> {});
                    return ctx;
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
