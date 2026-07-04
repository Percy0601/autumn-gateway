package xyz.wewin.autumn.gateway.examples.consul.config.contoller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/config")
public class ConfigController {
    @Autowired
    private AuthProperties authProperties;

    @GetMapping("/config")
    public List<String> getConfig() {
        return authProperties.getWhiteList();
    }


}
