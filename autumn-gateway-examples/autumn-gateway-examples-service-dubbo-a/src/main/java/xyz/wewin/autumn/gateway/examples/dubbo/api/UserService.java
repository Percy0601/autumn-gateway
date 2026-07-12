package xyz.wewin.autumn.gateway.examples.dubbo.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public interface UserService {

    @GetMapping("/{id}")
    UserDTO getById(@PathVariable("id") Long id);

    // dubbo RPC 专用方法（不走 REST）
    List<UserDTO> listByDept(Long deptId);
}