package xyz.wewin.autumn.gateway.dashboard.service.gw;

import org.apache.thrift.TApplicationException;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.layered.TFramedTransport;

import java.util.List;

/**
 * autumn service framework 控制面 {@code ControlApi} 的轻量客户端。
 *
 * <p>框架的 {@code control.thrift} 只定义了 5 个方法，参数与返回值全为 string（check.port 为 i32），
 * 因此这里直接用 libthrift 的协议 API 手写编解码，不必依赖另一个仓库生成的 ControlApi stub：</p>
 *
 * <pre>
 *   TSocket(host, port) → TFramedTransport → TBinaryProtocol → TMultiplexedProtocol
 *   multiplexed service name = "xyz.wewin.autumn.api.ControlApi$Iface"
 * </pre>
 *
 * <p>服务端使用 {@code AttachableBinaryProtocol}（TBinaryProtocol 的子类，附加隐式参数），
 * 框架测试 ImplicitAttachmentTest 已验证标准 TBinaryProtocol 旧客户端可正常调用，
 * 所以这里不需要引入 autumn-api 的扩展协议类。</p>
 *
 * <p>控制面鉴权（{@code control.thrift} 里的 optional token）：框架侧只有在显式配置
 * {@code control.token} 时才校验，默认完全放行。本地/内网服务治理没有必要再叠一层密钥，
 * 因此本客户端<b>不发送该 optional 字段</b>，也不持有任何 token 配置。
 * 若将来框架启用 control.token，在 {@link #call} 里按 field id 补回 token 即可
 * （health/applicationInfo/providerInfo = 1，referenceConfig = 2，check = 3）。</p>
 *
 * <p>本类不是线程安全的，一次调用链用完即 close。</p>
 */
public final class AutumnControlClient implements AutoCloseable {

    /** 与 ControlApi.Iface.class.getName() 一致，服务端 TMultiplexedProcessor 的注册名 */
    public static final String SERVICE_NAME = "xyz.wewin.autumn.api.ControlApi$Iface";

    public static final String METHOD_HEALTH = "health";
    public static final String METHOD_APPLICATION_INFO = "applicationInfo";
    public static final String METHOD_PROVIDER_INFO = "providerInfo";
    public static final String METHOD_REFERENCE_CONFIG = "referenceConfig";
    public static final String METHOD_CHECK = "check";

    private final TTransport transport;
    private final TProtocol protocol;
    private int seqId;

    public AutumnControlClient(String host, int port, int connectTimeoutMillis, int socketTimeoutMillis)
            throws TException {
        TSocket socket = new TSocket(host, port);
        socket.setConnectTimeout(connectTimeoutMillis);
        socket.setTimeout(socketTimeoutMillis);
        this.transport = new TFramedTransport(socket);
        this.protocol = new TMultiplexedProtocol(new TBinaryProtocol(this.transport), SERVICE_NAME);
        this.transport.open();
    }

    /** 存活状态：正常返回 "OK"，未监听返回 "DOWN: ..." */
    public String health() throws TException {
        return call(METHOD_HEALTH, List.of());
    }

    /** 应用信息：ApplicationConfig.toString()，含 JVM / OS 等运行时信息 */
    public String applicationInfo() throws TException {
        return call(METHOD_APPLICATION_INFO, List.of());
    }

    /** 提供者信息 + 度量摘要：ProviderConfig.toString() + ", metrics={...}" */
    public String providerInfo() throws TException {
        return call(METHOD_PROVIDER_INFO, List.of());
    }

    /** 按服务名查询 consumer 侧引用（连接池）配置 */
    public String referenceConfig(String name) throws TException {
        return call(METHOD_REFERENCE_CONFIG, List.of(
                arg("name", TType.STRING, (short) 1, name == null ? "" : name)));
    }

    /** 让目标实例去探测 ip:port 的 health，返回 "true" / "false" */
    public String check(String ip, int port) throws TException {
        return call(METHOD_CHECK, List.of(
                arg("ip", TType.STRING, (short) 1, ip == null ? "" : ip),
                arg("port", TType.I32, (short) 2, port)));
    }

    /** 按方法名 + 参数列表发起一次调用，兼容前端 invoke 控制台的动态分发 */
    public String invoke(String method, List<Arg> args) throws TException {
        return call(method, args);
    }

    /**
     * 一次 Thrift CALL / REPLY 往返。
     *
     * <p>args struct 按 field id 写；result struct 只取 field 0（success，string），
     * 其它字段一律 skip。异常应答（token 错误等）转成 TApplicationException 抛出。</p>
     */
    private String call(String method, List<Arg> args) throws TException {
        protocol.writeMessageBegin(new TMessage(method, TMessageType.CALL, ++seqId));
        protocol.writeStructBegin(new TStruct(method + "_args"));
        for (Arg a : args) {
            protocol.writeFieldBegin(new TField(a.name(), a.type(), a.id()));
            if (a.type() == TType.STRING) {
                protocol.writeString(String.valueOf(a.value()));
            } else if (a.type() == TType.I32) {
                protocol.writeI32(((Number) a.value()).intValue());
            }
            protocol.writeFieldEnd();
        }
        protocol.writeFieldStop();
        protocol.writeStructEnd();
        protocol.writeMessageEnd();
        // TFramedTransport 必须在 flush 时才写出 4 字节帧长并发送
        protocol.getTransport().flush();

        TMessage reply = protocol.readMessageBegin();
        if (reply.type == TMessageType.EXCEPTION) {
            TApplicationException ex = TApplicationException.readFrom(protocol);
            protocol.readMessageEnd();
            throw ex;
        }
        String result = null;
        protocol.readStructBegin();
        while (true) {
            TField field = protocol.readFieldBegin();
            if (field.type == TType.STOP) {
                break;
            }
            if (field.id == 0 && field.type == TType.STRING) {
                result = protocol.readString();
            } else {
                TProtocolUtil.skip(protocol, field.type);
            }
            protocol.readFieldEnd();
        }
        protocol.readStructEnd();
        protocol.readMessageEnd();
        return result;
    }

    @Override
    public void close() {
        transport.close();
    }

    /** 一个 Thrift 字段：name 只用于调试，真正决定编码的是 type + id + value */
    public record Arg(String name, byte type, short id, Object value) {
    }

    public static Arg arg(String name, byte type, short id, Object value) {
        return new Arg(name, type, id, value);
    }
}
