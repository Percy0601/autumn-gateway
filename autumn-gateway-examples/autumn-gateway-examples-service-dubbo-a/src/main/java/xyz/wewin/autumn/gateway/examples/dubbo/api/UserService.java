package xyz.wewin.autumn.gateway.examples.dubbo.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public interface UserService {
    
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    UserDTO getById(@PathVariable("id") Long id);

    // dubbo RPC 专用方法（不走 REST）
    List<UserDTO> listByDept(Long deptId);
}