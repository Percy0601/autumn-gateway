package xyz.wewin.autumn.gateway.dashboard.service.gw;

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
import org.apache.thrift.TMultiplexedProcessor;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务治理客户端的自测：起一个真实的 TFramedTransport + TBinaryProtocol + TMultiplexedProcessor
 * 服务端，验证手写编解码能与框架控制面协议互通（不是纯 mock）。
 */
class AutumnControlClientTest {

    private static final String PROVIDER_INFO =
            "ProviderConfig(ip=127.0.0.1, port=30880, maxPoolSize=20), "
                    + "metrics={svcA={borrowed=10, succeeded=9, discarded=1}, "
                    + "svcB={borrowed=4, succeeded=4, discarded=0}}";

    @Test
    void parsesMetricsAndProviderConfig() {
        List<AutumnMetricsParser.ServiceMetric> metrics = AutumnMetricsParser.parse(PROVIDER_INFO);
        assertEquals(2, metrics.size());
        // 按 borrowed 降序
        assertEquals("svcA", metrics.get(0).service());
        assertEquals(10, metrics.get(0).borrowed());
        assertEquals(9, metrics.get(0).succeeded());
        assertEquals(1, metrics.get(0).discarded());
        assertEquals(90.0, metrics.get(0).successRate());
        assertEquals(10.0, metrics.get(0).discardRate());
        assertEquals(100.0, metrics.get(1).successRate());

        Map<String, String> config = AutumnMetricsParser.parseProviderConfig(PROVIDER_INFO);
        assertEquals("127.0.0.1", config.get("ip"));
        assertEquals("30880", config.get("port"));
        assertFalse(config.containsKey("metrics"));
    }

    @Test
    void neverFailsOnMissingOrMalformedMetrics() {
        assertTrue(AutumnMetricsParser.parse(null).isEmpty());
        assertTrue(AutumnMetricsParser.parse("").isEmpty());
        assertTrue(AutumnMetricsParser.parse("ProviderConfig(ip=127.0.0.1)").isEmpty());
        assertTrue(AutumnMetricsParser.parse("metrics={}").isEmpty());
        assertTrue(AutumnMetricsParser.parseProviderConfig(null).isEmpty());
    }

    @Test
    void invokesControlApiOverFramedMultiplexedBinaryProtocol() throws Exception {
        TServerSocket serverSocket = new TServerSocket(new InetSocketAddress("127.0.0.1", 0));
        int port = serverSocket.getServerSocket().getLocalPort();
        List<String> seenMethods = new ArrayList<>();
        List<Integer> seenFieldIds = new ArrayList<>();

        TProcessor echo = (in, out) -> {
            TMessage msg = in.readMessageBegin();
            seenMethods.add(msg.name);
            in.readStructBegin();
            while (true) {
                TField field = in.readFieldBegin();
                if (field.type == TType.STOP) {
                    break;
                }
                seenFieldIds.add((int) field.id);
                TProtocolUtil.skip(in, field.type);
                in.readFieldEnd();
            }
            in.readStructEnd();
            in.readMessageEnd();

            out.writeMessageBegin(new TMessage(msg.name, TMessageType.REPLY, msg.seqid));
            out.writeStructBegin(new TStruct(msg.name + "_result"));
            out.writeFieldBegin(new TField("success", TType.STRING, (short) 0));
            out.writeString("providerInfo".equals(msg.name) ? PROVIDER_INFO : "OK");
            out.writeFieldEnd();
            out.writeFieldStop();
            out.writeStructEnd();
            out.writeMessageEnd();
            out.getTransport().flush();
        };

        TMultiplexedProcessor multiplexed = new TMultiplexedProcessor();
        multiplexed.registerProcessor(AutumnControlClient.SERVICE_NAME, echo);
        TServer server = new TSimpleServer(new TServer.Args(serverSocket)
                .processor(multiplexed)
                .transportFactory(new TFramedTransport.Factory())
                .protocolFactory(new TBinaryProtocol.Factory()));
        Thread serverThread = new Thread(server::serve, "control-api-mock");
        serverThread.setDaemon(true);
        serverThread.start();
        for (int i = 0; i < 50 && !server.isServing(); i++) {
            Thread.sleep(20);
        }
        assertTrue(server.isServing(), "mock thrift server did not start");

        try (AutumnControlClient client = new AutumnControlClient("127.0.0.1", port, 1000, 2000)) {
            assertEquals("OK", client.health());
            String providerInfo = client.providerInfo();
            assertTrue(providerInfo.contains("metrics="));
            assertEquals(2, AutumnMetricsParser.parse(providerInfo).size());
            assertEquals("OK", client.applicationInfo());
            assertEquals("OK", client.referenceConfig("svcA"));
            assertEquals("OK", client.check("127.0.0.1", 30880));
        } finally {
            server.stop();
            serverSocket.close();
        }

        assertEquals(List.of("health", "providerInfo", "applicationInfo", "referenceConfig", "check"),
                seenMethods);
        // 不发送 optional token 字段：前三个方法 0 个字段，referenceConfig 1 个，check 2 个
        assertEquals(List.of(1, 1, 2), seenFieldIds);
    }
}
