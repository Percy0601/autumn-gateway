package xyz.wewin.autumn.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class AutumnGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutumnGatewayApplication.class, args);
	}

	@Bean
	@Primary
	@ConditionalOnProperty("rateLimiter.non-secure")
	KeyResolver userKeyResolver() {
		return exchange -> Mono.just("1");
	}

	@Bean
	@ConditionalOnProperty("rateLimiter.secure")
	KeyResolver authUserKeyResolver() {
		return exchange -> ReactiveSecurityContextHolder.getContext()
				.map(ctx -> ctx.getAuthentication().getPrincipal().toString());
	}

	@Bean
	SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
		http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
				.httpBasic(org.springframework.security.config.Customizer.withDefaults());
		http.csrf(ServerHttpSecurity.CsrfSpec::disable);
		return http.build();
	}

}
