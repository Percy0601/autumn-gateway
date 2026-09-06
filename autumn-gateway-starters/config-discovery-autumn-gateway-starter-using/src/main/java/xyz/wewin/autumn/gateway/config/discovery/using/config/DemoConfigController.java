package xyz.wewin.autumn.gateway.config.discovery.using.config;

import java.util.Map;

import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示两种绑定方式在 Consul 配置变更后均能刷新：
 * - {@code @Value} 注入（demo.message）
 * - {@code @ConfigurationProperties} 绑定 Bean（DemoProperties）
 */
@RefreshScope
@RestController
@RequestMapping("/demo")
public class DemoConfigController {

    private final DemoProperties demoProperties;

    public DemoConfigController(DemoProperties demoProperties) {
        this.demoProperties = demoProperties;
    }

    @GetMapping("/message")
    public Map<String, Object> message(@org.springframework.beans.factory.annotation.Value("${demo.message:default-message}") String valueMessage) {
        return Map.of(
                "value-annotation", valueMessage,
                "configuration-properties", demoProperties.getMessage(),
                "feature-enabled", demoProperties.isFeatureEnabled()
        );
    }
}
