package xyz.wewin.autumn.gateway.examples.consul.config.listener;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ConsulRouteConfig {

    @Bean
    public ConsulRouteDefinitionLocator consulRouteDefinitionLocator(Environment environment) {
        return new ConsulRouteDefinitionLocator(environment);
    }
}
