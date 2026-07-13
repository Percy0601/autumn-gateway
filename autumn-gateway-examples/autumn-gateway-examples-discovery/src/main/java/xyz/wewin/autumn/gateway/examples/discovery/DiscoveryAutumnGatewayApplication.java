package xyz.wewin.autumn.gateway.examples.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan("xyz.wewin.autumn.gateway")
@SpringBootApplication
public class DiscoveryAutumnGatewayApplication {

	static void main(String[] args) {
		SpringApplication.run(DiscoveryAutumnGatewayApplication.class, args);
	}


}
