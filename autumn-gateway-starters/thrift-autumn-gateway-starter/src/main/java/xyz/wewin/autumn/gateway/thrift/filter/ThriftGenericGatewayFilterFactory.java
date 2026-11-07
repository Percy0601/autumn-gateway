package xyz.wewin.autumn.gateway.thrift.filter;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.wewin.autumn.gateway.thrift.autoconfigure.ThriftGenericProperties;
import xyz.wewin.autumn.gateway.thrift.autoconfigure.ThriftGenericProperties.Argument;
import xyz.wewin.autumn.gateway.thrift.autoconfigure.ThriftGenericProperties.ArgumentSource;
import xyz.wewin.autumn.gateway.thrift.autoconfigure.ThriftGenericProperties.Mapping;
import xyz.wewin.autumn.gateway.thrift.autoconfigure.ThriftGenericProperties.ResponseMode;
import xyz.wewin.autumn.gateway.thrift.support.ThriftCallExecutor;
import xyz.wewin.autumn.gateway.thrift.support.ThriftServiceCatalog;
import xyz.wewin.autumn.gateway.thrift.support.ThriftServiceCatalog.ThriftEndpoint;
import xyz.wewin.autumn.protocol.generic.GenericRequest;
import xyz.wewin.autumn.protocol.generic.GenericResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

/**
 * 路由过滤器 {@code ThriftGeneric}：把一次 HTTP 请求泛化转换成对后端 Thrift 服务的调用，
 * 并把 {@link GenericResponse#asMap()} 直接作为 HTTP 响应体返回。
 *
 * <p>方向是单向的、明确的：<b>HTTP 进 → Thrift 出 → JSON 回</b>。
 * 网关不做 Thrift 反向代理，也不做 Thrift 转 HTTP，后端仍然是普通的 Thrift 服务。</p>
 *
 * <h3>两种用法</h3>
 * <dl>
 *   <dt>固定映射（一条路由一个方法）</dt>
 *   <dd>{@code ThriftGeneric=demoService,sayHello}</dd>
 *   <dt>动态分发（一条路由覆盖整个服务，这才是"泛化"的常态用法）</dt>
 *   <dd>{@code ThriftGeneric} 不带参数，服务名与方法名从下面三处按优先级取：
 *       <ol>
 *         <li>路径变量：{@code Path=/thrift/{service}/{method}}</li>
 *         <li>请求头：{@code X-Thrift-Service} / {@code X-Thrift-Method}</li>
 *       </ol>
 *       前两处都没有、路由也没写死时返回 400，不会瞎猜。</dd>
 * </dl>
 *
 * <h3>为什么短路而不是继续 filter chain</h3>
 * 它自己写响应、<b>不调用 {@code chain.filter}</b>，因此 SCG 的 {@code NettyRoutingFilter} 不会执行，
 * 路由的 {@code uri} 只是语义占位（写 {@code thrift://<service>}）。这也保证了
 * "thrift 路由不会顺带发出一次 HTTP 请求"。
 *
 * <h3>默认关闭</h3>
 * {@code autumn.thrift.enabled=false} 时过滤器仍在（这样配错路由不会在启动期报"找不到过滤器"），
 * 但每个请求直接得到 503 {@code thrift_disabled}，不碰网络、不读 body。
 *
 * <h3>线程模型</h3>
 * thrift 调用是阻塞 IO，统一丢到 {@code boundedElastic}；同时用 {@link Semaphore} 限制在飞调用数，
 * 拿不到令牌直接 503 快速失败，避免把弹性线程池排满后拖垮同一进程里的 HTTP 转发。
 */
public class ThriftGenericGatewayFilterFactory
        extends AbstractGatewayFilterFactory<ThriftGenericGatewayFilterFactory.Config> {

    /** 路由里写的过滤器名：{@code ThriftGeneric} 或 {@code ThriftGeneric=demoService,sayHello} */
    public static final String NAME = "ThriftGeneric";

    /** 动态分发时从请求头取服务名/方法名的默认键 */
    public static final String SERVICE_HEADER = "X-Thrift-Service";

    public static final String METHOD_HEADER = "X-Thrift-Method";

    private static final Logger log = LoggerFactory.getLogger(ThriftGenericGatewayFilterFactory.class);

    private static final String COST_HEADER = "X-Thrift-Cost-Millis";

    private final ThriftGenericProperties properties;

    private final ThriftServiceCatalog catalog;

    private final ThriftCallExecutor executor;

    private final ObjectMapper objectMapper;

    private final Semaphore concurrencyGate;

    public ThriftGenericGatewayFilterFactory(ThriftGenericProperties properties,
                                             ThriftServiceCatalog catalog,
                                             ThriftCallExecutor executor,
                                             ObjectMapper objectMapper) {
        super(Config.class);
        this.properties = properties;
        this.catalog = catalog;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.concurrencyGate = new Semaphore(Math.max(1, properties.getMaxConcurrentCalls()));
    }

    /** 短写顺序：{@code ThriftGeneric=<service>,<method>[,<mapping>]}；service/method 可省略，走动态分发 */
    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("service", "method", "mapping");
    }

    @Override
    public GatewayFilter apply(Config config) {
        Config cfg = (config == null) ? new Config() : config;

        if (!this.properties.isEnabled()) {
            return (exchange, chain) -> writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "thrift_disabled",
                    "autumn.thrift.enabled=false，ThriftGeneric 路由未启用");
        }

        String fixedService = trim(cfg.getService());
        String fixedMethod = trim(cfg.getMethod());
        // 只写了一半是配置错误：要么都写死，要么都留空走动态分发
        if (StringUtils.hasText(fixedService) != StringUtils.hasText(fixedMethod)) {
            return (exchange, chain) -> writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "bad_route_config",
                    NAME + " 的服务名与方法名必须同时配置或同时省略（省略时从路径变量/请求头动态解析）");
        }
        // 写死的情况下在装配期就把"服务没登记"拦下来，不用等第一笔流量
        if (StringUtils.hasText(fixedService) && this.catalog.endpoint(fixedService).isEmpty()) {
            return (exchange, chain) -> writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "unknown_service",
                    unknownServiceMessage(fixedService));
        }
        if (StringUtils.hasText(fixedService)) {
            log.info("ThriftGeneric 固定映射已装配：{} -> {}.{}", fixedService,
                    this.catalog.endpoint(fixedService).orElseThrow().host(), fixedMethod);
        }

        String mappingName = trim(cfg.getMapping());
        final Mapping mapping = StringUtils.hasText(mappingName) ? findMapping(mappingName) : null;
        if (StringUtils.hasText(mappingName) && mapping == null) {
            String message = "映射规则 " + mappingName + " 未在 autumn.thrift.mappings 中配置";
            return (exchange, chain) -> writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                    "unknown_mapping", message);
        }

        long budget = (long) this.properties.getConnectTimeoutMillis() + this.properties.getSocketTimeoutMillis();

        return (exchange, chain) -> {
            Target target = resolveTarget(exchange, fixedService, fixedMethod);
            if (target.errorStatus() != null) {
                return writeError(exchange, target.errorStatus(), target.errorType(), target.errorMessage());
            }
            ThriftEndpoint endpoint = this.catalog.endpoint(target.service()).orElse(null);
            if (endpoint == null) {
                return writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "unknown_service",
                        unknownServiceMessage(target.service()));
            }
            if (!this.concurrencyGate.tryAcquire()) {
                return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "thrift_busy",
                        "thrift 并发已达上限 " + this.properties.getMaxConcurrentCalls() + "，请稍后重试");
            }
            return readBody(exchange)
                    .map(body -> buildArguments(exchange, body, mapping))
                    .flatMap(parsed -> Mono.fromCallable(
                                    () -> invoke(exchange, endpoint, target, parsed.value()))
                            .subscribeOn(Schedulers.boundedElastic()))
                    .timeout(Duration.ofMillis(budget + 1000))
                    .flatMap(response -> writeResponse(exchange, response, mapping))
                    .onErrorResume(IllegalArgumentException.class, e -> writeError(exchange,
                            HttpStatus.BAD_REQUEST, "bad_request", e.getMessage()))
                    .onErrorResume(TimeoutException.class, e -> writeError(exchange,
                            HttpStatus.GATEWAY_TIMEOUT, "timeout",
                            "thrift 调用超过 " + budget + "ms 未返回"))
                    .onErrorResume(e -> {
                        log.warn("ThriftGeneric 调用异常：{}.{}", target.service(), target.method(), e);
                        return writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "gateway_error",
                                "thrift 调用未完成：" + e.getClass().getSimpleName());
                    })
                    .doFinally(signal -> this.concurrencyGate.release());
        };
    }

    /**
     * 目标服务/方法解析：写死的优先，其次是路径变量 {@code {service}} / {@code {method}}，
     * 最后是请求头。都不满足就不猜，返回 400。
     */
    private Target resolveTarget(ServerWebExchange exchange, String fixedService, String fixedMethod) {
        if (StringUtils.hasText(fixedService)) {
            return Target.ok(fixedService, fixedMethod);
        }

        String service = null;
        String method = null;

        Map<String, String> variables = exchange.getAttribute(ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables != null) {
            service = trim(variables.get("service"));
            method = trim(variables.get("method"));
        }
        if (!StringUtils.hasText(service)) {
            service = trim(exchange.getRequest().getHeaders().getFirst(SERVICE_HEADER));
        }
        if (!StringUtils.hasText(method)) {
            method = trim(exchange.getRequest().getHeaders().getFirst(METHOD_HEADER));
        }

        if (!StringUtils.hasText(service)) {
            return Target.error(HttpStatus.BAD_REQUEST, "bad_request",
                    "无法确定 thrift 服务名：请把路由写成 " + NAME + "=<service>,<method>，"
                            + "或在路径里带 {service}/{method}，或加请求头 " + SERVICE_HEADER);
        }
        if (!StringUtils.hasText(method)) {
            return Target.error(HttpStatus.BAD_REQUEST, "bad_request",
                    "已经解析到服务 " + service + "，但无法确定方法名："
                            + "请在路径里带 {method}，或加请求头 " + METHOD_HEADER);
        }
        return Target.ok(service, method);
    }

    private GenericResponse invoke(ServerWebExchange exchange, ThriftEndpoint endpoint,
                                   Target target, Object arguments) {
        GenericRequest request = GenericRequest.of(endpoint.host(), endpoint.port(), target.service(), target.method())
                .setConnectTimeout(this.properties.getConnectTimeoutMillis())
                .setSocketTimeout(this.properties.getSocketTimeoutMillis())
                .setFramed(endpoint.framed())
                .setMultiplexed(endpoint.multiplexed())
                .setMultiplexedName(endpoint.multiplexedName())
                .setArguments(arguments)
                .setAttachments(attachments(exchange));
        return this.executor.invoke(request);
    }

    /** HTTP 头 → attachment key；默认只翻泳道头，不抄 Authorization 之类的敏感头。 */
    private Map<String, String> attachments(ServerWebExchange exchange) {
        Map<String, String> mapping = this.properties.getAttachmentHeaderMapping();
        if (mapping == null || mapping.isEmpty()) {
            return Map.of();
        }
        Map<String, String> attachments = new LinkedHashMap<>();
        ServerHttpRequest request = exchange.getRequest();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String value = request.getHeaders().getFirst(entry.getKey());
            if (StringUtils.hasText(value)) {
                attachments.put(entry.getValue(), value);
            }
        }
        return attachments;
    }

    /** 请求体读取：读成字符串，空 body 给空串（无参方法） */
    private Mono<String> readBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(this::asText)
                .defaultIfEmpty("");
    }

    private String asText(DataBuffer buffer) {
        try {
            return buffer.toString(StandardCharsets.UTF_8);
        }
        finally {
            DataBufferUtils.release(buffer);
        }
    }

    /**
     * 参数拼装：
     * <ul>
     *   <li>没有映射规则 → 请求体整体作为参数（对象按参数名，数组按声明顺序）；</li>
     *   <li>有映射规则 → 先按 {@code include-body} 决定是否用请求体打底，再按规则逐条从
     *       header / queryString / path 变量 / 常量取值覆盖，写成 thrift 的参数名。</li>
     * </ul>
     */
    private Arguments buildArguments(ServerWebExchange exchange, String text, Mapping mapping) {
        if (mapping == null) {
            return new Arguments(parseBody(text));
        }

        Map<String, Object> args = new LinkedHashMap<>();
        if (mapping.isIncludeBody() && StringUtils.hasText(text)) {
            Object parsed = parseBody(text);
            if (parsed == null) {
                throw new IllegalArgumentException("映射规则要求 include-body=true，但请求体为空");
            }
            if (!(parsed instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("include-body=true 时请求体必须是 JSON 对象");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) parsed;
            args.putAll(body);
        }

        List<Argument> rules = mapping.getArguments();
        for (Argument rule : rules) {
            String arg = rule.getArg();
            if (!StringUtils.hasText(arg)) {
                continue;
            }
            String key = StringUtils.hasText(rule.getKey()) ? rule.getKey() : arg;
            Object value = resolve(exchange, args, rule, key);
            if (value == null && StringUtils.hasText(rule.getDefaultValue())) {
                value = rule.getDefaultValue();
            }
            if (value == null) {
                if (rule.isRequired()) {
                    throw new IllegalArgumentException("缺少必填参数 " + arg + "（来源 " + rule.getSource() + ":" + key + "）");
                }
                continue;
            }
            args.put(arg, value);
        }
        return new Arguments(args);
    }

    private Object resolve(ServerWebExchange exchange, Map<String, Object> body, Argument rule, String key) {
        ArgumentSource source = rule.getSource() == null ? ArgumentSource.BODY : rule.getSource();
        return switch (source) {
            case BODY -> body.get(key);
            case QUERY -> exchange.getRequest().getQueryParams().getFirst(key);
            case HEADER -> exchange.getRequest().getHeaders().getFirst(key);
            case PATH -> {
                Map<String, String> variables =
                        exchange.getAttribute(ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                yield variables == null ? null : variables.get(key);
            }
            case FIXED -> rule.getValue();
        };
    }

    private Object parseBody(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Object value;
        try {
            value = this.objectMapper.readValue(text, Object.class);
        }
        catch (JacksonException e) {
            throw new IllegalArgumentException("请求体不是合法 JSON：" + e.getMessage(), e);
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return value;
        }
        throw new IllegalArgumentException("请求体必须是 JSON 对象（按参数名）或数组（按声明顺序）");
    }

    private Mapping findMapping(String name) {
        List<Mapping> mappings = this.properties.getMappings();
        if (mappings == null) {
            return null;
        }
        for (Mapping mapping : mappings) {
            if (mapping != null && name.equals(trim(mapping.getName()))) {
                return mapping;
            }
        }
        return null;
    }

    private Mono<Void> writeResponse(ServerWebExchange exchange, GenericResponse response, Mapping mapping) {
        HttpStatus status = statusOf(response);
        if (response.isSuccess() && mapping != null && mapping.getResponse() != null
                && mapping.getResponse().getSuccessStatus() != null) {
            status = HttpStatus.valueOf(mapping.getResponse().getSuccessStatus());
        }
        Object payload;
        if (response.isSuccess() && mapping != null && mapping.getResponse() != null
                && mapping.getResponse().getMode() == ResponseMode.DATA) {
            // 只要 data：业务方拿到的就是干净的结果，不再套一层信封
            payload = response.getValue();
        }
        else {
            Map<String, Object> body = response.asMap();
            if (!response.isSuccess() && !this.properties.isExposeErrorDetail()
                    && !GenericResponse.ERROR_BAD_REQUEST.equals(response.getErrorType())) {
                body.put("message", "thrift 调用失败（详情已隐藏，可打开 autumn.thrift.expose-error-detail）");
                body.remove("error");
            }
            payload = body;
        }

        ServerHttpResponse httpResponse = exchange.getResponse();
        httpResponse.setStatusCode(status);
        httpResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        httpResponse.getHeaders().set(COST_HEADER, Long.toString(response.getCostMillis()));
        return write(httpResponse, payload);
    }

    private static HttpStatus statusOf(GenericResponse response) {
        if (response.isSuccess()) {
            return HttpStatus.OK;
        }
        String errorType = response.getErrorType();
        if (GenericResponse.ERROR_BAD_REQUEST.equals(errorType)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (GenericResponse.ERROR_SERVICE.equals(errorType)) {
            // 后端声明的业务异常：请求本身合法，是业务说"不行"
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (GenericResponse.ERROR_APPLICATION.equals(errorType)) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    private String unknownServiceMessage(String service) {
        return "服务 " + service + " 未在 autumn.thrift.services 中登记，已登记：" + this.catalog.serviceNames();
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status,
                                  String errorType, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("errorType", errorType);
        body.put("message", message);
        body.put("path", exchange.getRequest().getURI().getPath());
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return write(response, body);
    }

    private Mono<Void> write(ServerHttpResponse response, Object body) {
        byte[] bytes;
        try {
            bytes = this.objectMapper.writeValueAsBytes(body);
        }
        catch (JacksonException e) {
            bytes = ("{\"success\":false,\"errorType\":\"gateway_error\",\"message\":\""
                    + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** 解析后的参数；{@code value} 为 null 表示无参方法。 */
    private record Arguments(Object value) {
    }

    /** 一次请求的目标服务与方法，或解析失败的原因。 */
    private record Target(String service, String method, HttpStatus errorStatus,
                          String errorType, String errorMessage) {

        static Target ok(String service, String method) {
            return new Target(service, method, null, null, null);
        }

        static Target error(HttpStatus status, String type, String message) {
            return new Target(null, null, status, type, message);
        }
    }

    /**
     * 路由过滤器配置：短写 {@code ThriftGeneric=<service>,<method>[,<mapping>]}。
     * 前两项都省略时为动态分发，目标从路径变量/请求头解析；
     * 第三项是 {@code autumn.thrift.mappings[].name}，即入参/出参映射规则。
     * 其余项（超时、multiplexed 等）走服务级配置，避免每条路由重复写一遍。
     */
    public static class Config {

        /** 已登记的服务名（{@code autumn.thrift.services[].name} 或 IDL 中的 service 名）；空表示动态解析 */
        private String service;

        /** thrift 方法名；空表示动态解析 */
        private String method;

        /** 入参/出参映射规则名；空表示请求体整体作为参数 */
        private String mapping;

        public String getService() {
            return this.service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public String getMethod() {
            return this.method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getMapping() {
            return this.mapping;
        }

        public void setMapping(String mapping) {
            this.mapping = mapping;
        }
    }
}
