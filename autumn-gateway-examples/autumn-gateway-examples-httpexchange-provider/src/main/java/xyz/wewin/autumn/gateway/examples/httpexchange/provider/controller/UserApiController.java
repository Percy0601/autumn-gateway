package xyz.wewin.autumn.gateway.examples.httpexchange.provider.controller;

import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import xyz.wewin.autumn.gateway.examples.httpexchange.UserClient;
import xyz.wewin.autumn.gateway.examples.httpexchange.UserDto;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class UserApiController implements UserClient {

    private final Map<Long, UserDto> store = new ConcurrentHashMap<>();

    public UserApiController() {
        store.put(1L, new UserDto(1L, "Alice", "alice@test.com"));
        store.put(2L, new UserDto(2L, "Bob", "bob@test.com"));
    }

    @Override
    public Mono<UserDto> getById(Long id) {
        return Mono.justOrEmpty(store.get(id));
    }

    @Override
    public Mono<Void> create(UserDto dto) {
        store.put(dto.getId(), dto);
        return Mono.empty();
    }

}
