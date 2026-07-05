package xyz.wewin.autumn.gateway.examples.opentelemetry.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import reactor.core.publisher.Mono;

/**
 *
 * @author: baoxin.zhao
 * @date: 7/5/26
 */

@Component
public class TraceGlobalFilter implements GlobalFilter, Ordered {
    private Logger log = LoggerFactory.getLogger(this.getClass());
    private final Tracer tracer;

    // 注入我们手动创建的 OpenTelemetry Bean
    public TraceGlobalFilter(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("gateway");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Span span = tracer.spanBuilder("gateway.request")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        log.info(">>> Span started: {}", span.getSpanContext().getTraceId());
        span.setAttribute("http.path", exchange.getRequest().getPath().value());

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    span.end();
                    log.info(">>> Span ended");
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
