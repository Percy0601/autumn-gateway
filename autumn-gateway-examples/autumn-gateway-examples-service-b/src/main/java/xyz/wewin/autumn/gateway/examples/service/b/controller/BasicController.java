package xyz.wewin.autumn.gateway.examples.service.b.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class BasicController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @GetMapping("/hello")
    public String hello(String name) {
        if(!StringUtils.hasText(name)) {
            name = "hello";
        }
        log.info("===hello===: {}", name);
        return "service-b: ".concat(name);
    }


    @RestController
    public static class HealthController {

        @GetMapping("/health")
        public Map<String, String> health() {
            return Map.of("status", "UP");
        }
    }
}
