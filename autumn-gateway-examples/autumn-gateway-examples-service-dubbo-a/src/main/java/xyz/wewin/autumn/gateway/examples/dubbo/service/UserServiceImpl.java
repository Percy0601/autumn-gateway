package xyz.wewin.autumn.gateway.examples.dubbo.service;

import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import xyz.wewin.autumn.gateway.examples.dubbo.api.UserDTO;
import xyz.wewin.autumn.gateway.examples.dubbo.api.UserService;

import java.util.ArrayList;
import java.util.List;
@DubboService(
        protocol = "dubbo,triple",   // ← 双协议！[3](@ref)
        version = "1.0.0"
)
@Service
public class UserServiceImpl implements UserService {
    @Override
    public UserDTO getById(Long id) {
        UserDTO u = new UserDTO();
        u.setId(id);
        u.setName("Alice");
        return u;
    }

    @Override
    public List<UserDTO> listByDept(Long deptId) {
        // 这个方法只在 dubbo 协议暴露，rest 不会挂 /api/users/listByDept
        // 因为接口上没有 @GetMapping

        List<UserDTO> users = new ArrayList<>();
        UserDTO user1 = new UserDTO();
        user1.setId(1L);
        user1.setName("Alice");
        users.add(user1);

        UserDTO user2 = new UserDTO();
        user2.setId(2L);
        user2.setName("Bob");
        users.add(user2);
        return users;
    }
}
