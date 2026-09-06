package xyz.wewin.autumn.gateway.discovery.consul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.SmartLifecycle;

public class ConsulAutoServiceRegistration implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConsulAutoServiceRegistration.class);

    private final ServiceRegistry<Registration> registry;
    private final Registration registration;

    private volatile boolean running;
    private volatile boolean deregistered;

    public ConsulAutoServiceRegistration(ServiceRegistry<Registration> registry, Registration registration) {
        this.registry = registry;
        this.registration = registration;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        registry.register(registration);
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        deregistered = true;
        try {
            registry.deregister(registration);
        } catch (Exception e) {
            log.warn("Deregister failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public int getPhase() {
        return 0;
    }

    public boolean isDeregistered() {
        return deregistered;
    }
}