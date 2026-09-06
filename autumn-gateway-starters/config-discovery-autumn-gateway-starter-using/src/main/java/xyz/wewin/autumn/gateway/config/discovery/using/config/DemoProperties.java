package xyz.wewin.autumn.gateway.config.discovery.using.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 演示 @ConfigurationProperties + @RefreshScope：
 * Consul 配置变更 → EnvironmentChangeEvent → 本 Bean 重建并重新绑定最新值。
 * Consul KV（key 即 property key）：
 *   config/application/demo.message=hello-from-application
 *   config/{应用名}/demo.message=hello-from-app
 *   config/{应用名}/demo.feature-enabled=true
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    private String message = "default-message";
    private boolean featureEnabled = false;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isFeatureEnabled() {
        return featureEnabled;
    }

    public void setFeatureEnabled(boolean featureEnabled) {
        this.featureEnabled = featureEnabled;
    }
}
