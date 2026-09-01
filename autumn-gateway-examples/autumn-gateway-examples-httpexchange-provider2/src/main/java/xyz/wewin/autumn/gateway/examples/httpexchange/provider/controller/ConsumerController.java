package xyz.wewin.autumn.gateway.examples.httpexchange.provider.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import xyz.wewin.autumn.gateway.examples.httpexchange.UserClient;
import xyz.wewin.autumn.gateway.examples.httpexchange.UserDto;

/**
 * 本模块作为调用方（consumer）的演示入口：
 * 通过 HttpServiceProxyFactory 生成的 {@link UserClient} 远程代理发起调用，
 * 请求地址为 lb://httpexchage-provider/users/{id}，由 LoadBalancer 在
 * httpexchage-provider 的多个实例（8083 / 8085）之间轮询分发，实现负载均衡。
 */
@RestController
@RequestMapping("/provider2")
public class ConsumerController {

    private static final Log log = LogFactory.getLog(ConsumerController.class);

    private final UserClient userClient;

    public ConsumerController(UserClient userClient) {
        this.userClient = userClient;
    }

    @GetMapping("/get-user/{id}")
    public Mono<UserDto> getUser(@PathVariable("id") Long id) {
        log.info("=== load balance call: lb://httpexchage-provider/users/" + id + " ===");
        return userClient.getById(id);
    }
}
