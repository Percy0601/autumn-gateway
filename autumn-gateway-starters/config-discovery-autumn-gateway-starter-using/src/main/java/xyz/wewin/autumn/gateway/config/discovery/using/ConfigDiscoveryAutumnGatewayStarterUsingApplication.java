package xyz.wewin.autumn.gateway.config.discovery.using;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * config-discovery starter 使用示例：
 * 1. application.properties 中通过 spring.config.import=consul: 导入 Consul 配置；
 * 2. /demo/** 演示 @Value / @ConfigurationProperties 动态刷新（需配 Consul KV）；
 * 3. 网关路由键也放入 Consul，变更时监听 EnvironmentChangeEvent 自动刷新。
 */
@SpringBootApplication
public class ConfigDiscoveryAutumnGatewayStarterUsingApplication {

    static void main(String[] args) {
        SpringApplication.run(ConfigDiscoveryAutumnGatewayStarterUsingApplication.class, args);
    }
}
