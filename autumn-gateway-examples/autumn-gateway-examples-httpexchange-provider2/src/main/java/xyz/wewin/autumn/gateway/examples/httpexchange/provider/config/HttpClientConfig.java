package xyz.wewin.autumn.gateway.examples.httpexchange.provider.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.HttpServiceGroup;
import org.springframework.web.service.registry.ImportHttpServices;
import xyz.wewin.autumn.gateway.examples.httpexchange.UserClient;

// 本示例采用 HttpExchangeConfig（显式 HttpServiceProxyFactory + @LoadBalanced WebClient.Builder）方式生成远程代理。
// 若想改用 @ImportHttpServices（Spring 6.2 声明式 HTTP 接口注册），去掉下方注释即可（group 即服务名，自动拼 lb:// 前缀）：
//@Configuration
//@ImportHttpServices(
//        group = "httpexchage-provider",
//        types = UserClient.class,
//        clientType = HttpServiceGroup.ClientType.WEB_CLIENT   // ← WebFlux / Gateway 必选；默认是 RestClient(同步)
//)
public class HttpClientConfig {
}
