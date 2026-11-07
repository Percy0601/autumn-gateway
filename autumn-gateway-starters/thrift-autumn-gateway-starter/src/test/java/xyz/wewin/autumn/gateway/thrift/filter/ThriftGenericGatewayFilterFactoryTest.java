package xyz.wewin.autumn.gateway.thrift.filter;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.wewin.autumn.gateway.thrift.autoconfigure.ThriftGenericProperties;
import xyz.wewin.autumn.gateway.thrift.support.ThriftCallExecutor;
import xyz.wewin.autumn.gateway.thrift.support.ThriftServiceCatalog;
import xyz.wewin.autumn.protocol.generic.GenericRequest;
import xyz.wewin.autumn.protocol.generic.GenericResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 过滤器单元测试：不动网络，用桩执行器覆盖开关、路由配置错误、状态码映射、参数解析与 attachment 映射。
 *
 * <p>真实 TCP 往返另见 {@link ThriftGenericTcpRoundTripTest}，这里只保证 HTTP 语义正确。</p>
 */
class ThriftGenericGatewayFilterFactoryTest {

    private static final String IDL = """
            namespace java xyz.wewin.autumn.gateway.thrift.demo

            service DemoService {
              string echo(1: string name)
            }
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ThriftGenericProperties properties;

    private ThriftServiceCatalog catalog;

    private ThriftGenericGatewayFilterFactory factory;

    private final AtomicReference<GenericRequest> captured = new AtomicReference<>();

    private final AtomicBoolean chainCalled = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        this.properties = new ThriftGenericProperties();
        this.properties.setEnabled(true);

        ThriftGenericProperties.Service service = new ThriftGenericProperties.Service();
        service.setName("DemoService");
        service.setHost("127.0.0.1");
        service.setPort(9999);
        service.setIdl(IDL);
        this.properties.setServices(List.of(service));

        this.catalog = ThriftServiceCatalog.load(this.properties, new DefaultResourceLoader());
        this.factory = build(request -> GenericResponse.success("ok", 1));
        this.captured.set(null);
        this.chainCalled.set(false);
        this.factory = build(request -> GenericResponse.success("ok", 1));
    }

    // ------------------------------------------------------------------ 开关

    @Test
    void disabledSwitchShortCircuitsWithoutTouchingTheBackend() {
        this.properties.setEnabled(false);
        this.factory = build(request -> {
            throw new AssertionError("开关关闭时不应发起调用");
        });

        Map<String, Object> body = executeWith("DemoService", "echo", applicationJson("{\"name\":\"x\"}"),
                HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body).containsEntry("success", false).containsEntry("errorType", "thrift_disabled");
        assertThat(this.chainCalled).isFalse();
    }

    // ------------------------------------------------------------------ 路由配置

    @Test
    void unknownServiceIsReportedAsConfigurationError() {
        ThriftGenericProperties disabledByConfig = new ThriftGenericProperties();
        disabledByConfig.setEnabled(true);
        ThriftServiceCatalog emptyCatalog = ThriftServiceCatalog.load(disabledByConfig, new DefaultResourceLoader());
        ThriftGenericGatewayFilterFactory factory = new ThriftGenericGatewayFilterFactory(
                disabledByConfig, emptyCatalog, request -> GenericResponse.success("ok", 1), MAPPER);

        MockServerWebExchange exchange = MockServerWebExchange.from(applicationJson("{}"));
        factory.apply(config("NotRegistered", "echo")).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void emptyRouteArgsWithoutDynamicSourceIsRejectedWith400() {
        this.factory = build(request -> {
            throw new AssertionError("解析不到目标时不应发起调用");
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(applicationJson("{}"));
        this.factory.apply(new ThriftGenericGatewayFilterFactory.Config()).filter(exchange, chain()).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void halfConfiguredRouteArgsAreRejectedAtAssemblyTime() {
        ThriftGenericGatewayFilterFactory.Config config = new ThriftGenericGatewayFilterFactory.Config();
        config.setService("DemoService");
        this.factory = build(request -> {
            throw new AssertionError("只写服务名不应发起调用");
        });

        MockServerWebExchange exchange = MockServerWebExchange.from(applicationJson("{}"));
        this.factory.apply(config).filter(exchange, chain()).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ------------------------------------------------------------------ 动态分发（泛化）

    @Test
    void dynamicDispatchResolvesTargetFromPathVariables() {
        this.factory = build(request -> GenericResponse.success(request.getMethod(), 1));

        MockServerWebExchange exchange = MockServerWebExchange.from(applicationJson("{\"name\":\"x\"}"));
        exchange.getAttributes().put(ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("service", "DemoService", "method", "echo"));

        this.factory.apply(new ThriftGenericGatewayFilterFactory.Config()).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(this.captured.get().getService()).isEqualTo("DemoService");
        assertThat(this.captured.get().getMethod()).isEqualTo("echo");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"echo\"");
    }

    @Test
    void dynamicDispatchFallsBackToThriftHeaders() {
        this.factory = build(request -> GenericResponse.success(request.getService() + "." + request.getMethod(), 1));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/thrift/any")
                        .header(ThriftGenericGatewayFilterFactory.SERVICE_HEADER, "DemoService")
                        .header(ThriftGenericGatewayFilterFactory.METHOD_HEADER, "echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        this.factory.apply(new ThriftGenericGatewayFilterFactory.Config()).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(this.captured.get().getService()).isEqualTo("DemoService");
        assertThat(this.captured.get().getMethod()).isEqualTo("echo");
    }

    @Test
    void dynamicDispatchWithUnknownServiceIsRejected() {
        this.factory = build(request -> {
            throw new AssertionError("未登记的服务不应发起调用");
        });

        MockServerWebExchange exchange = MockServerWebExchange.from(applicationJson("{}"));
        exchange.getAttributes().put(ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("service", "Ghost", "method", "echo"));

        this.factory.apply(new ThriftGenericGatewayFilterFactory.Config()).filter(exchange, chain()).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ------------------------------------------------------------------ 成功路径

    @Test
    void successReturnsDataAndCostHeaderWithoutContinuingTheChain() {
        this.factory = build(request -> GenericResponse.success(Map.of("hello", "gateway"), 12));

        MockServerWebExchange exchange = MockServerWebExchange.from(applicationJson("{\"name\":\"gateway\"}"));
        this.factory.apply(config("DemoService", "echo")).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Thrift-Cost-Millis")).isEqualTo("12");
        assertThat(body(exchange)).contains("\"success\":true").contains("\"hello\":\"gateway\"");
        assertThat(this.chainCalled).isFalse();
    }

    @Test
    void arrayBodyIsForwardedAsPositionalArguments() {
        this.factory = build(request -> GenericResponse.success(request.getArguments(), 1));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/thrift/demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[\"positional\"]"));
        this.factory.apply(config("DemoService", "echo")).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(exchange)).contains("positional");
        assertThat(this.captured.get().getArguments()).isInstanceOf(List.class);
    }

    // ------------------------------------------------------------------ 失败映射

    @Test
    void badRequestMapsTo400AndKeepsTheMessage() {
        this.factory = build(request ->
                GenericResponse.failure(GenericResponse.ERROR_BAD_REQUEST, "缺少必填参数 name", null, 0));

        Map<String, Object> body = executeWith("DemoService", "echo", applicationJson("{\"nam\":\"typo\"}"),
                HttpStatus.BAD_REQUEST);
        assertThat(body).containsEntry("errorType", "BAD_REQUEST").containsEntry("message", "缺少必填参数 name");
    }

    @Test
    void declaredServiceExceptionMapsTo500AndHidesDetailsByDefault() {
        this.factory = build(request ->
                GenericResponse.failure(GenericResponse.ERROR_SERVICE, "业务异常", Map.of("code", 1), 3));

        Map<String, Object> body = executeWith("DemoService", "echo", applicationJson("{\"name\":\"x\"}"),
                HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body).containsEntry("errorType", "SERVICE");
        // 默认不透出后端详情，只留一句可操作的提示
        assertThat(body.get("message")).asString().contains("expose-error-detail");
        assertThat(body).doesNotContainKey("error");
    }

    @Test
    void applicationExceptionMapsTo502() {
        this.factory = build(request ->
                GenericResponse.failure(GenericResponse.ERROR_APPLICATION, "unknown method", null, 1));
        assertThat(executeWith("DemoService", "echo", applicationJson("{}"), HttpStatus.BAD_GATEWAY))
                .containsEntry("errorType", "APPLICATION");
    }

    @Test
    void transportErrorMapsTo503() {
        this.factory = build(request ->
                GenericResponse.failure(GenericResponse.ERROR_TRANSPORT, "connection refused", null, 1));
        assertThat(executeWith("DemoService", "echo", applicationJson("{}"), HttpStatus.SERVICE_UNAVAILABLE))
                .containsEntry("errorType", "TRANSPORT");
    }

    @Test
    void exposeErrorDetailPassesBackendMessageThrough() {
        this.properties.setExposeErrorDetail(true);
        this.factory = build(request ->
                GenericResponse.failure(GenericResponse.ERROR_APPLICATION, "boom: upstream", null, 1));
        assertThat(executeWith("DemoService", "echo", applicationJson("{}"), HttpStatus.BAD_GATEWAY))
                .containsEntry("message", "boom: upstream");
    }

    @Test
    void invalidJsonBodyIsRejectedWith400() {
        this.factory = build(request -> GenericResponse.success("ok", 1));
        Map<String, Object> body = executeWith("DemoService", "echo", applicationJson("{not-json"),
                HttpStatus.BAD_REQUEST);
        assertThat(body).containsEntry("errorType", "bad_request");
    }

    // ------------------------------------------------------------------ attachment

    @Test
    void trafficHeaderIsTranslatedIntoThriftAttachment() {
        this.factory = build(request -> GenericResponse.success("ok", 1));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/thrift/demo")
                        .header("X-Traffic", "gray")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"x\"}"));
        this.factory.apply(config("DemoService", "echo")).filter(exchange, chain()).block();

        GenericRequest request = this.captured.get();
        assertThat(request).isNotNull();
        assertThat(request.getAttachments()).containsEntry("traffic", "gray");
        assertThat(request.isFramed()).isTrue();
        assertThat(request.isMultiplexed()).isFalse();
        assertThat(request.getService()).isEqualTo("DemoService");
        assertThat(request.getMethod()).isEqualTo("echo");
        assertThat(request.getHost()).isEqualTo("127.0.0.1");
        assertThat(request.getPort()).isEqualTo(9999);
    }

    @Test
    void absentTrafficHeaderProducesNoAttachment() {
        this.factory = build(request -> GenericResponse.success("ok", 1));
        MockServerWebExchange exchange = MockServerWebExchange.from(applicationJson("{\"name\":\"x\"}"));
        this.factory.apply(config("DemoService", "echo")).filter(exchange, chain()).block();

        assertThat(this.captured.get().getAttachments()).isEmpty();
    }

    // ------------------------------------------------------------------ 入参/出参映射（对应 ShenYu 的 param mapping）

    @Test
    void mappingPullsArgumentsFromQueryHeaderPathAndFixedValue() {
        ThriftGenericProperties.Mapping mapping = new ThriftGenericProperties.Mapping();
        mapping.setName("demoMapping");
        mapping.setArguments(List.of(
                argument("name", ThriftGenericProperties.ArgumentSource.QUERY, "user", true),
                argument("tenant", ThriftGenericProperties.ArgumentSource.HEADER, "X-Tenant", false),
                argument("id", ThriftGenericProperties.ArgumentSource.PATH, "id", false),
                argument("source", ThriftGenericProperties.ArgumentSource.FIXED, null, false, "gateway")));
        this.properties.setMappings(List.of(mapping));
        this.factory = build(request -> GenericResponse.success(request.getArguments(), 1));

        ThriftGenericGatewayFilterFactory.Config config = config("DemoService", "echo");
        config.setMapping("demoMapping");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/thrift/demo?user=alice")
                        .header("X-Tenant", "t1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"extra\":\"keep\"}"));
        exchange.getAttributes().put(ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "42"));

        this.factory.apply(config).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) this.captured.get().getArguments();
        assertThat(args).containsEntry("name", "alice")
                .containsEntry("tenant", "t1")
                .containsEntry("id", "42")
                .containsEntry("source", "gateway")
                .containsEntry("extra", "keep");
    }

    @Test
    void mappingRequiredArgumentMissingIsRejectedWith400() {
        ThriftGenericProperties.Mapping mapping = new ThriftGenericProperties.Mapping();
        mapping.setName("strict");
        mapping.setArguments(List.of(argument("name", ThriftGenericProperties.ArgumentSource.QUERY, "user", true)));
        this.properties.setMappings(List.of(mapping));
        this.factory = build(request -> {
            throw new AssertionError("缺必填参数时不应发起调用");
        });

        ThriftGenericGatewayFilterFactory.Config config = config("DemoService", "echo");
        config.setMapping("strict");

        MockServerWebExchange exchange = MockServerWebExchange.from(applicationJson("{}"));
        this.factory.apply(config).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(exchange)).contains("name");
    }

    @Test
    void unknownMappingNameIsRejectedAtAssemblyTime() {
        this.factory = build(request -> {
            throw new AssertionError("映射规则不存在时不应发起调用");
        });

        ThriftGenericGatewayFilterFactory.Config config = config("DemoService", "echo");
        config.setMapping("ghost");

        MockServerWebExchange exchange = MockServerWebExchange.from(applicationJson("{}"));
        this.factory.apply(config).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body(exchange)).contains("unknown_mapping");
    }

    @Test
    void responseModeDataReturnsBareValue() {
        ThriftGenericProperties.Mapping mapping = new ThriftGenericProperties.Mapping();
        mapping.setName("bare");
        ThriftGenericProperties.Response response = new ThriftGenericProperties.Response();
        response.setMode(ThriftGenericProperties.ResponseMode.DATA);
        mapping.setResponse(response);
        this.properties.setMappings(List.of(mapping));
        this.factory = build(request -> GenericResponse.success(Map.of("hello", "gateway"), 7));

        ThriftGenericGatewayFilterFactory.Config config = config("DemoService", "echo");
        config.setMapping("bare");

        MockServerWebExchange exchange = MockServerWebExchange.from(applicationJson("{\"name\":\"x\"}"));
        this.factory.apply(config).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        // 只有 data 本身，没有 success/costMillis 信封
        assertThat(body(exchange)).isEqualTo("{\"hello\":\"gateway\"}");
    }

    private static ThriftGenericProperties.Argument argument(String arg,
                                                             ThriftGenericProperties.ArgumentSource source,
                                                             String key, boolean required) {
        return argument(arg, source, key, required, null);
    }

    private static ThriftGenericProperties.Argument argument(String arg,
                                                             ThriftGenericProperties.ArgumentSource source,
                                                             String key, boolean required, String value) {
        ThriftGenericProperties.Argument argument = new ThriftGenericProperties.Argument();
        argument.setArg(arg);
        argument.setSource(source);
        argument.setKey(key);
        argument.setRequired(required);
        argument.setValue(value);
        return argument;
    }

    // ------------------------------------------------------------------ helpers

    private ThriftGenericGatewayFilterFactory build(ThriftCallExecutor stub) {
        return new ThriftGenericGatewayFilterFactory(this.properties, this.catalog, request -> {
            this.captured.set(request);
            return stub.invoke(request);
        }, MAPPER);
    }

    private Map<String, Object> executeWith(String service, String method, MockServerHttpRequest request,
                                            HttpStatus expected) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        this.factory.apply(config(service, method)).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expected);
        assertThat(this.chainCalled).isFalse();
        return parse(body(exchange));
    }

    private GatewayFilterChain chain() {
        return exchange -> {
            this.chainCalled.set(true);
            return Mono.error(new IllegalStateException("ThriftGeneric 必须短路，不能继续 filter chain"));
        };
    }

    private static MockServerHttpRequest applicationJson(String body) {
        return MockServerHttpRequest.post("/thrift/demo")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private static ThriftGenericGatewayFilterFactory.Config config(String service, String method) {
        ThriftGenericGatewayFilterFactory.Config config = new ThriftGenericGatewayFilterFactory.Config();
        config.setService(service);
        config.setMethod(method);
        return config;
    }

    private static String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        }
        catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON：" + json, e);
        }
    }
}
