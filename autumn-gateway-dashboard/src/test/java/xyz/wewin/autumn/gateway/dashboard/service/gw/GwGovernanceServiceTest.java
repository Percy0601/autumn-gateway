package xyz.wewin.autumn.gateway.dashboard.service.gw;

import org.apache.thrift.TMultiplexedProcessor;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwServiceInstance;
import xyz.wewin.autumn.gateway.dashboard.repo.GwServiceInstanceRepository;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务治理自测：H2 建表脚本可执行 + Service 层通过真实 Thrift 服务端完成观测/度量/invoke。
 * 不启动 Spring 上下文，避免依赖本机 H2 TCP 服务与登录态。
 */
class GwGovernanceServiceTest {

    private static final String PROVIDER_INFO =
            "ProviderConfig(ip=127.0.0.1, port=30880, maxPoolSize=20), "
                    + "metrics={svcA={borrowed=10, succeeded=9, discarded=1}}";

    @Test
    void h2SchemaCreatesServiceInstanceTable() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:governance_schema_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                        + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/gateway/gw_schema_h2.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(1) FROM gw_service_instance", Integer.class));
        jdbc.update("INSERT INTO gw_service_instance (service_name, host, port, remark) VALUES (?,?,?,?)",
                "demo", "127.0.0.1", 30880, "test");
        assertEquals("demo", jdbc.queryForObject("SELECT service_name FROM gw_service_instance", String.class));
    }

    @Test
    void observeOverviewAndInvokeOverRealThriftServer() throws Exception {
        TServerSocket serverSocket = new TServerSocket(new InetSocketAddress("127.0.0.1", 0));
        int port = serverSocket.getServerSocket().getLocalPort();

        TProcessor handler = (in, out) -> {
            TMessage msg = in.readMessageBegin();
            in.readStructBegin();
            while (true) {
                TField field = in.readFieldBegin();
                if (field.type == TType.STOP) {
                    break;
                }
                TProtocolUtil.skip(in, field.type);
                in.readFieldEnd();
            }
            in.readStructEnd();
            in.readMessageEnd();
            String value = switch (msg.name) {
                case "providerInfo" -> PROVIDER_INFO;
                case "applicationInfo" -> "ApplicationConfig(name=demo-provider, jvm=25)";
                case "referenceConfig" -> "ReferenceConfig(name=svcA, maxPoolSize=20)";
                case "check" -> "true";
                default -> "OK";
            };
            out.writeMessageBegin(new TMessage(msg.name, TMessageType.REPLY, msg.seqid));
            out.writeStructBegin(new TStruct(msg.name + "_result"));
            out.writeFieldBegin(new TField("success", TType.STRING, (short) 0));
            out.writeString(value);
            out.writeFieldEnd();
            out.writeFieldStop();
            out.writeStructEnd();
            out.writeMessageEnd();
            out.getTransport().flush();
        };
        TMultiplexedProcessor multiplexed = new TMultiplexedProcessor();
        multiplexed.registerProcessor(AutumnControlClient.SERVICE_NAME, handler);
        TServer server = new TSimpleServer(new TServer.Args(serverSocket)
                .processor(multiplexed)
                .transportFactory(new TFramedTransport.Factory())
                .protocolFactory(new TBinaryProtocol.Factory()));
        Thread thread = new Thread(server::serve, "governance-service-mock");
        thread.setDaemon(true);
        thread.start();
        for (int i = 0; i < 50 && !server.isServing(); i++) {
            Thread.sleep(20);
        }
        assertTrue(server.isServing());

        try {
            GwServiceInstance instance = new GwServiceInstance();
            instance.setId(1L);
            instance.setServiceName("demo-provider");
            instance.setHost("127.0.0.1");
            instance.setPort(port);
            instance.setStatus(1);

            GwGovernanceService service = new GwGovernanceService(stubRepo(instance), 1000, 2000);

            Map<String, Object> observed = service.observe(instance);
            assertEquals(true, observed.get("reachable"));
            assertEquals(true, observed.get("healthy"));
            assertEquals("127.0.0.1", ((Map<?, ?>) observed.get("providerConfig")).get("ip"));
            assertEquals(1, ((List<?>) observed.get("metrics")).size());
            assertEquals(10L, ((AutumnMetricsParser.ServiceMetric)
                    ((List<?>) observed.get("metrics")).get(0)).borrowed());

            assertEquals(1, service.overview().size());
            assertEquals(5, service.methods().size());
            assertTrue(service.invoke(instance, "providerInfo", Map.of()).contains("metrics="));
            assertTrue(service.invoke(instance, "referenceConfig", Map.of("name", "svcA"))
                    .contains("ReferenceConfig"));
            assertEquals("true", service.invoke(instance, "check",
                    Map.of("ip", "127.0.0.1", "port", "30880")));
            assertThrows(IllegalArgumentException.class,
                    () -> service.invoke(instance, "unknownMethod", Map.of()));
        } finally {
            server.stop();
            serverSocket.close();
        }
    }

    /** 只实现服务治理用到的方法，其余返回 null（本测试不会调用） */
    private static GwServiceInstanceRepository stubRepo(GwServiceInstance instance) {
        return (GwServiceInstanceRepository) Proxy.newProxyInstance(
                GwServiceInstanceRepository.class.getClassLoader(),
                new Class<?>[]{GwServiceInstanceRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAllEnabled", "findAllActive" -> List.of(instance);
                    case "findActiveById" -> Optional.of(instance);
                    case "save" -> args[0];
                    case "toString" -> "stubRepo";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
