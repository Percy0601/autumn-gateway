package xyz.wewin.autumn.gateway.limit.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import xyz.wewin.autumn.gateway.limit.MemoryRateLimiterGatewayFilterFactory;

/**
 * limit-autumn-gateway 自动配置：
 * 注册 {@link MemoryRateLimiterGatewayFilterFactory}（路由上以 name=MemoryRateLimiter 使用）
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.cloud.gateway.filter.GlobalFilter")
@EnableConfigurationProperties(LimitAutumnGatewayProperties.class)
public class LimitAutumnGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = LimitAutumnGatewayProperties.PREFIX, name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public MemoryRateLimiterGatewayFilterFactory memoryRateLimiterGatewayFilterFactory(
            LimitAutumnGatewayProperties properties) {
        MemoryRateLimiterGatewayFilterFactory.Config defaultConfig = new MemoryRateLimiterGatewayFilterFactory.Config();
        defaultConfig.setReplenishRate(properties.getReplenishRate());
        defaultConfig.setBurstCapacity(properties.getBurstCapacity());
        defaultConfig.setTimeWindowSeconds(properties.getTimeWindowSeconds());
        return new MemoryRateLimiterGatewayFilterFactory(defaultConfig);
    }

}
