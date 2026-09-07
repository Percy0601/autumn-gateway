package xyz.wewin.autumn.gateway.examples.httpexchange;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

@HttpExchange("/users")
//@HttpExchange(url = "lb://user-service", accept = "application/json", contentType = "application/json")
public interface UserClient {

    @GetExchange("/{id}")
    Mono<UserDto> getById(@PathVariable("id") Long id);

    @PostExchange
    Mono<Void> create(@RequestBody UserDto dto);
}
