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
    private Thread shutdownHook;

    public ConsulAutoServiceRegistration(ServiceRegistry<Registration> registry, Registration registration) {
        this.registry = registry;
        this.registration = registration;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        registry.register(registration);
        running = true;
        registerShutdownHook();
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        deregisterNow();
        deregistered = true;
        unregisterShutdownHook();
    }

    private synchronized void deregisterNow() {
        if (deregistered) return;
        try {
            registry.deregister(registration);
            deregistered = true;
        } catch (Exception e) {
            log.warn("Deregister failed: {}", e.getMessage());
        }
    }

    private void registerShutdownHook() {
        if (shutdownHook != null) return;
        shutdownHook = new Thread(() -> {
            log.info("JVM shutdown hook triggered, deregistering...");
            deregisterNow();
        }, "consul-deregister-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void unregisterShutdownHook() {
        if (shutdownHook == null) return;
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM 已经在关闭中了
        }
        shutdownHook = null;
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