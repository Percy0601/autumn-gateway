package xyz.wewin.autumn.gateway.thrift.support;

import xyz.wewin.autumn.protocol.generic.GenericRequest;
import xyz.wewin.autumn.protocol.generic.GenericResponse;
import xyz.wewin.autumn.protocol.generic.GenericThriftInvoker;

/**
 * 默认实现：直接委托给 {@code autumn-protocol-extension} 的 {@link GenericThriftInvoker}。
 *
 * <p>注意：invoker 每次调用都会新建 {@code TSocket} 并 open/close，还没有连接池。
 * P0 先保证"能调通"，连接池与实例发现放在 P1（见 roadmap），
 * 所以 {@code max-concurrent-calls} 这个闸门在 P0 阶段承担了主要的保护职责。</p>
 */
public class DefaultThriftCallExecutor implements ThriftCallExecutor {

    private final GenericThriftInvoker invoker;

    public DefaultThriftCallExecutor(ThriftServiceCatalog catalog) {
        this.invoker = new GenericThriftInvoker(catalog.registry());
    }

    @Override
    public GenericResponse invoke(GenericRequest request) {
        return this.invoker.invoke(request);
    }

}
