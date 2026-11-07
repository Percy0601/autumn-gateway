package xyz.wewin.autumn.gateway.thrift.autoconfigure;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import xyz.wewin.autumn.gateway.thrift.filter.ThriftGenericGatewayFilterFactory;
import xyz.wewin.autumn.gateway.thrift.support.DefaultThriftCallExecutor;
import xyz.wewin.autumn.gateway.thrift.support.ThriftCallExecutor;
import xyz.wewin.autumn.gateway.thrift.support.ThriftServiceCatalog;
import xyz.wewin.autumn.protocol.generic.GenericThriftInvoker;

/**
 * Thrift 泛化路由自动配置。
 *
 * <h3>注册了什么</h3>
 * <ul>
 *   <li>{@link ThriftServiceCatalog}：启动期解析 {@code autumn.thrift.services[*]} 的 IDL；</li>
 *   <li>{@link ThriftCallExecutor}：默认委托 {@link GenericThriftInvoker}；</li>
 *   <li>{@link ThriftGenericGatewayFilterFactory}：路由过滤器 {@code ThriftGeneric}。</li>
 * </ul>
 *
 * <h3>为什么不按 {@code enabled} 做条件装配</h3>
 * 如果开关一关就把过滤器工厂拿掉，任何引用了 {@code ThriftGeneric} 的路由都会在启动期
 * 报"找不到过滤器"，把"关掉能力"变成"启动失败"。这里让工厂始终存在，
 * 由它自己按开关短路返回 503，配置错误只在运行期以明确文案暴露。
 * 真正的"零侵入"由 deploy 侧的 profile 保证：不选 {@code component-thrift} 时，
 * 这个 starter 根本不在 classpath 上。
 *
 * <h3>IDL 有问题时</h3>
 * 直接抛出 {@link IllegalStateException} 让应用启动失败。带着半份服务描述表上线，
 * 只会在第一笔流量进来时报一堆 500，不如启动时就拦下来。
 */
@AutoConfiguration
@ConditionalOnClass({AbstractGatewayFilterFactory.class, GenericThriftInvoker.class})
@EnableConfigurationProperties(ThriftGenericProperties.class)
public class ThriftGenericAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ThriftGenericAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ThriftServiceCatalog thriftServiceCatalog(ThriftGenericProperties properties,
                                                     ResourceLoader resourceLoader) {
        ThriftServiceCatalog catalog = ThriftServiceCatalog.load(properties, resourceLoader);
        if (!properties.isEnabled()) {
            log.info("thrift 泛化路由已引入但未启用（autumn.thrift.enabled=false），已登记 {} 个服务描述",
                    catalog.serviceNames().size());
        }
        else if (catalog.serviceNames().isEmpty()) {
            log.warn("autumn.thrift.enabled=true 但 autumn.thrift.services 为空，ThriftGeneric 路由将不可用");
        }
        else {
            log.info("thrift 泛化路由已启用，服务：{}", catalog.serviceNames());
        }
        return catalog;
    }

    @Bean
    @ConditionalOnMissingBean
    public ThriftCallExecutor thriftCallExecutor(ThriftServiceCatalog catalog) {
        return new DefaultThriftCallExecutor(catalog);
    }

    @Bean
    @ConditionalOnMissingBean
    public ThriftGenericGatewayFilterFactory thriftGenericGatewayFilterFactory(
            ThriftGenericProperties properties,
            ThriftServiceCatalog catalog,
            ThriftCallExecutor executor,
            ObjectProvider<ObjectMapper> objectMapper) {
        return new ThriftGenericGatewayFilterFactory(properties, catalog, executor,
                objectMapper.getIfAvailable(ObjectMapper::new));
    }

}
