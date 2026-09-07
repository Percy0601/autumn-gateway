package xyz.wewin.autumn.gateway.registry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AutumnRegistrar {
    @Autowired
    AutumnAutoServiceRegistration autumnAutoServiceRegistration;
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        autumnAutoServiceRegistration.start(); // 但注意 AbstractAutoServiceRegistration 默认 auto-startup=true
    }

}
