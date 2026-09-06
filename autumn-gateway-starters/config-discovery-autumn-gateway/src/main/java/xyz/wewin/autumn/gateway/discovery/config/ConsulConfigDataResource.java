package xyz.wewin.autumn.gateway.discovery.config;

import java.util.Objects;

import org.springframework.boot.context.config.ConfigDataResource;

public class ConsulConfigDataResource extends ConfigDataResource {

    private final ConsulProperties consulProperties;
    private final ConsulConfigProperties configProperties;

    public ConsulConfigDataResource(ConsulProperties consulProperties,
                                    ConsulConfigProperties configProperties) {
        this.consulProperties = consulProperties;
        this.configProperties = configProperties;
    }

    public ConsulProperties getConsulProperties() { return consulProperties; }

    public ConsulConfigProperties getProperties() { return configProperties; }

    public String identity() {
        return consulProperties.getHost() + ":" + consulProperties.getPort()
                + "/" + configProperties.getPrefix() + "/" + configProperties.getApplication();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsulConfigDataResource that)) return false;
        return Objects.equals(identity(), that.identity());
    }

    @Override
    public int hashCode() { return Objects.hash(identity()); }

    @Override
    public String toString() { return "consul-config[" + identity() + "]"; }
}