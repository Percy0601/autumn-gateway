package xyz.wewin.autumn.gateway.examples.httpexchange.consumer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.HttpServiceGroup;
import org.springframework.web.service.registry.ImportHttpServices;
import xyz.wewin.autumn.gateway.examples.httpexchange.UserClient;

@Configuration
@ImportHttpServices(
        group = "httpexchage-provider",
        types = UserClient.class,
        clientType = HttpServiceGroup.ClientType.WEB_CLIENT   // ← WebFlux / Gateway 必选；默认是 RestClient(同步)
)
public class HttpClientConfig {
}
