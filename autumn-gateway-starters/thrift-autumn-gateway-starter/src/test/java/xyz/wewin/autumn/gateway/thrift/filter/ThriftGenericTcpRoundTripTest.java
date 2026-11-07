package xyz.wewin.autumn.gateway.thrift.filter;

import tools.jackson.databind.ObjectMapper;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.apache.thrift.transport.TServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.wewin.autumn.api.extension.AttachableBinaryProtocol;
import xyz.wewin.autumn.api.extension.AttachmentContext;
import xyz.wewin.autumn.gateway.thrift.autoconfigure.ThriftGenericProperties;
import xyz.wewin.autumn.gateway.thrift.support.DefaultThriftCallExecutor;
import xyz.wewin.autumn.gateway.thrift.support.ThriftServiceCatalog;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 TCP 往返：网关过滤器 → IDL 解析 → 按 field id 编码 → TBinaryProtocol(framed) → 手写 TProcessor
 * → 解码响应 → JSON 回给调用方。
 *
 * <p>服务端刻意不用生成代码，与 {@code autumn-protocol-extension} 自己的测试同一种写法：
 * 只要两边都不依赖 stub，就能证明"网关完全靠 IDL 在运行期编解码"这件事是真的。</p>
 */
class ThriftGenericTcpRoundTripTest {

    private static final String IDL = """
            namespace java xyz.wewin.autumn.gateway.thrift.demo

            service DemoService {
              string echo(1: string name)
            }
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TServer server;

    private TServerSocket serverTransport;

    private int port;

    @BeforeEach
    void startThriftServer() throws Exception {
        this.serverTransport = new TServerSocket(new InetSocketAddress("127.0.0.1", 0));
        this.port = this.serverTransport.getServerSocket().getLocalPort();
        this.server = new TSimpleServer(new TServer.Args(this.serverTransport)
                .processor(new EchoProcessor())
                .transportFactory(new TFramedTransport.Factory())
                .protocolFactory(new AttachableBinaryProtocol.Factory()));
        Thread thread = new Thread(this.server::serve, "thrift-gateway-roundtrip");
        thread.setDaemon(true);
        thread.start();
        for (int i = 0; i < 100 && !this.server.isServing(); i++) {
            Thread.sleep(10);
        }
        assertThat(this.server.isServing()).as("thrift 测试服务未启动").isTrue();
    }

    @AfterEach
    void stopThriftServer() {
        if (this.server != null) {
            this.server.stop();
        }
        if (this.serverTransport != null) {
            this.serverTransport.close();
        }
    }

    @Test
    void invokesRealThriftServerAndReturnsJsonWithAttachment() {
        ThriftGenericGatewayFilterFactory factory = factoryFor(this.port);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/thrift/demo")
                        .header("X-Traffic", "gray")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"gateway\"}"));

        factory.apply(config("DemoService", "echo")).filter(exchange, chain()).block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Thrift-Cost-Millis")).isNotBlank();
        assertThat(body).contains("\"success\":true");
        // 服务端把我们编码的 name 参数解出来了，并且读到了 field-0 attachment 里的泳道
        assertThat(body).contains("hello gateway").contains("traffic=gray");
    }

    @Test
    void unreachableBackendMapsTo503Transport() {
        ThriftGenericGatewayFilterFactory factory = factoryFor(1);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/thrift/demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"x\"}"));

        factory.apply(config("DemoService", "echo")).filter(exchange, chain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"errorType\":\"TRANSPORT\"");
    }

    // ------------------------------------------------------------------ helpers

    private ThriftGenericGatewayFilterFactory factoryFor(int targetPort) {
        ThriftGenericProperties properties = new ThriftGenericProperties();
        properties.setEnabled(true);
        properties.setConnectTimeoutMillis(500);
        properties.setSocketTimeoutMillis(2000);

        ThriftGenericProperties.Service service = new ThriftGenericProperties.Service();
        service.setName("DemoService");
        service.setHost("127.0.0.1");
        service.setPort(targetPort);
        service.setIdl(IDL);
        properties.setServices(List.of(service));

        ThriftServiceCatalog catalog = ThriftServiceCatalog.load(properties, new DefaultResourceLoader());
        return new ThriftGenericGatewayFilterFactory(properties, catalog,
                new DefaultThriftCallExecutor(catalog), MAPPER);
    }

    private static ThriftGenericGatewayFilterFactory.Config config(String service, String method) {
        ThriftGenericGatewayFilterFactory.Config config = new ThriftGenericGatewayFilterFactory.Config();
        config.setService(service);
        config.setMethod(method);
        return config;
    }

    private static GatewayFilterChain chain() {
        return exchange -> Mono.error(new IllegalStateException("ThriftGeneric 必须短路"));
    }

    /**
     * 手写的最小 echo 服务端：读 message → 读 args（field-0 attachment 由
     * {@code AttachableBinaryProtocol.readFieldBegin()} 在读取第一个字段时顺带解析进
     * {@link AttachmentContext}）→ 回 "hello {name}/traffic={lane}"。
     */
    private static final class EchoProcessor implements TProcessor {

        @Override
        public void process(TProtocol in, TProtocol out) throws TException {
            try {
                TMessage message = in.readMessageBegin();
                String name = "-";

                in.readStructBegin();
                while (true) {
                    TField field = in.readFieldBegin();
                    if (field.type == TType.STOP) {
                        break;
                    }
                    if (field.id == 1 && field.type == TType.STRING) {
                        name = in.readString();
                    }
                    else {
                        TProtocolUtil.skip(in, field.type);
                    }
                    in.readFieldEnd();
                }
                in.readStructEnd();
                in.readMessageEnd();

                // 参数读完（含 field-0）之后，attachment 已经落在服务端上下文里
                String lane = AttachmentContext.get("traffic");

                out.writeMessageBegin(new TMessage(message.name, TMessageType.REPLY, message.seqid));
                out.writeStructBegin(new TStruct(message.name + "_result"));
                out.writeFieldBegin(new TField("success", TType.STRING, (short) 0));
                out.writeString("hello " + name + "/traffic=" + (lane == null ? "-" : lane));
                out.writeFieldEnd();
                out.writeFieldStop();
                out.writeStructEnd();
                out.writeMessageEnd();
                out.getTransport().flush();
            }
            finally {
                AttachmentContext.clear();
            }
        }
    }
}
