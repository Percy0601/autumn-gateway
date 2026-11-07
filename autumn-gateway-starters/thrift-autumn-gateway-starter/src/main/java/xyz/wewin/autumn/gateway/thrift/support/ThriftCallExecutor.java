package xyz.wewin.autumn.gateway.thrift.support;

import xyz.wewin.autumn.protocol.generic.GenericRequest;
import xyz.wewin.autumn.protocol.generic.GenericResponse;

/**
 * 一次泛化调用的执行入口。
 *
 * <p>把 {@code GenericThriftInvoker} 收在接口后面，是为了让网关侧的单测不需要真开一个
 * thrift 服务：状态码映射、参数解析、并发闸门都能用桩实现覆盖，真实 TCP 往返另有专门的测试。</p>
 */
@FunctionalInterface
public interface ThriftCallExecutor {

    GenericResponse invoke(GenericRequest request);

}
