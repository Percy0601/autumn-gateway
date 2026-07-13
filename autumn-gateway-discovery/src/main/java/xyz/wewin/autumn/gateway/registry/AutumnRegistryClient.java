package xyz.wewin.autumn.gateway.registry;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutumnRegistryClient {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    private String server;

    public AutumnRegistryClient(String server) {
        this.server = server;
    }

    public void register(AutumnRegistration registration) {
        log.info("client begin registry service: {}", registration);
    }

    public void deregister(@Nullable String instanceId) {
        log.info("client begin deregister service: {}", instanceId);
    }
}
