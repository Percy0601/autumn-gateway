package xyz.wewin.autumn.gateway.examples.consul.config.contoller;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private List<String> whiteList = new ArrayList<>(); // KV 里 "/auth/**,/public/**" → split ,

    public List<String> getWhiteList() {
        return whiteList;
    }

    public void setWhiteList(List<String> whiteList) {
        this.whiteList = whiteList;
    }
}
