package xyz.wewin.autumn.gateway.thrift.support;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import xyz.wewin.autumn.gateway.thrift.autoconfigure.ThriftGenericProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 服务目录装配：IDL 来源、服务名匹配、坏输入必须在启动期失败。
 */
class ThriftServiceCatalogTest {

    private static final String IDL = """
            service DemoService {
              string echo(1: string name)
            }
            """;

    private final DefaultResourceLoader loader = new DefaultResourceLoader();

    @Test
    void inlineIdlRegistersServiceAndEndpoint() {
        ThriftServiceCatalog catalog = ThriftServiceCatalog.load(properties(service(null, 9001, IDL)),
                this.loader);

        assertThat(catalog.serviceNames()).containsExactly("DemoService");
        assertThat(catalog.require("DemoService").method("echo")).isNotNull();
        assertThat(catalog.endpoint("DemoService")).get()
                .extracting(ThriftServiceCatalog.ThriftEndpoint::port,
                        ThriftServiceCatalog.ThriftEndpoint::host,
                        ThriftServiceCatalog.ThriftEndpoint::framed)
                .containsExactly(9001, "127.0.0.1", true);
    }

    @Test
    void idlLocationLoadsFromClasspath() {
        ThriftGenericProperties.Service service = service("DemoService", 9002, null);
        service.setIdlLocation("classpath:idl/demo.thrift");

        ThriftServiceCatalog catalog = ThriftServiceCatalog.load(properties(service), this.loader);

        assertThat(catalog.serviceNames()).containsExactly("DemoService");
        assertThat(catalog.endpoint("DemoService")).get()
                .extracting(ThriftServiceCatalog.ThriftEndpoint::port).isEqualTo(9002);
    }

    @Test
    void singleServiceIdlDoesNotRequireExplicitName() {
        ThriftServiceCatalog catalog = ThriftServiceCatalog.load(properties(service(null, 9003, IDL)),
                this.loader);
        assertThat(catalog.serviceNames()).containsExactly("DemoService");
    }

    @Test
    void unknownServiceNameFailsFastWithAvailableNames() {
        assertThatThrownBy(() -> ThriftServiceCatalog.load(
                properties(service("Nope", 9004, IDL)), this.loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nope")
                .hasMessageContaining("DemoService");
    }

    @Test
    void brokenIdlFailsFast() {
        assertThatThrownBy(() -> ThriftServiceCatalog.load(
                properties(service(null, 9005, "service Broken { string echo(1: string name ")), this.loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDL");
    }

    @Test
    void missingIdlFailsFast() {
        assertThatThrownBy(() -> ThriftServiceCatalog.load(
                properties(service(null, 9006, null)), this.loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idl");
    }

    @Test
    void perServiceMultiplexedAndFramedOverrideGlobals() {
        ThriftGenericProperties properties = new ThriftGenericProperties();
        properties.setFramed(false);
        properties.setMultiplexed(false);

        ThriftGenericProperties.Service service = service("DemoService", 9007, IDL);
        service.setFramed(true);
        service.setMultiplexed(true);
        service.setMultiplexedName("svc");
        properties.setServices(List.of(service));

        ThriftServiceCatalog catalog = ThriftServiceCatalog.load(properties, this.loader);
        assertThat(catalog.endpoint("DemoService")).get()
                .extracting(ThriftServiceCatalog.ThriftEndpoint::framed,
                        ThriftServiceCatalog.ThriftEndpoint::multiplexed,
                        ThriftServiceCatalog.ThriftEndpoint::multiplexedName)
                .containsExactly(true, true, "svc");
    }

    private static ThriftGenericProperties properties(ThriftGenericProperties.Service service) {
        ThriftGenericProperties properties = new ThriftGenericProperties();
        properties.setServices(List.of(service));
        return properties;
    }

    private static ThriftGenericProperties.Service service(String name, int port, String idl) {
        ThriftGenericProperties.Service service = new ThriftGenericProperties.Service();
        service.setName(name);
        service.setPort(port);
        service.setIdl(idl);
        return service;
    }
}
