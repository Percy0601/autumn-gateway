package xyz.wewin.autumn.gateway.examples.httpexchange.consumer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.wewin.autumn.gateway.examples.httpexchange.UserClient;
import xyz.wewin.autumn.gateway.examples.httpexchange.UserDto;

import java.time.Duration;

@RestController
@RequestMapping("/consumer")
public class BasicController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    UserClient userClient;
    @GetMapping("/hello")
    public String hello(String name) {
        if(!StringUtils.hasText(name)) {
            name = "hello";
        }
        log.info("===hello===: {}", name);
        return "service-a: ".concat(name);
    }


    @GetMapping("/get-user")
    public UserDto getUser(@RequestParam Long id) {
        log.info("===user-client===: {}", (userClient == null));
        return userClient.getById(id).block(Duration.ofSeconds(1L));
    }

}
