package xyz.wewin.autumn.gateway.registry;

import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.context.ApplicationContext;

public class AutumnAutoServiceRegistration extends AbstractAutoServiceRegistration<AutumnRegistration> {

    private final AutumnRegistration registration;
    private final AutumnServiceRegistry serviceRegistry;

    public AutumnAutoServiceRegistration(ApplicationContext context,
                                         AutumnServiceRegistry serviceRegistry,
                                         AutumnRegistration registration) {
        super(context, serviceRegistry, new AutoServiceRegistrationProperties());
        this.serviceRegistry = serviceRegistry;
        this.registration = registration;
    }

    @Override
    protected Object getConfiguration() {
        return registration;
    }

    @Override
    protected boolean isEnabled() {
        return true;
    }

    @Override
    protected AutumnRegistration getRegistration() {
        return registration;
    }

    @Override
    protected AutumnRegistration getManagementRegistration() {
        return registration;
    }
}
