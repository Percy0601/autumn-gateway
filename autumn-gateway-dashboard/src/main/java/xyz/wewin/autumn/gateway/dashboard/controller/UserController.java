package xyz.wewin.autumn.gateway.dashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import xyz.wewin.autumn.gateway.dashboard.common.Result;
import xyz.wewin.autumn.gateway.dashboard.entity.User;
import xyz.wewin.autumn.gateway.dashboard.entity.UserApp;
import xyz.wewin.autumn.gateway.dashboard.entity.UserAuthAccount;
import xyz.wewin.autumn.gateway.dashboard.entity.UserRole;
import xyz.wewin.autumn.gateway.dashboard.service.UserService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public Result<List<User>> list(@RequestParam(defaultValue = "1") int current,
                                   @RequestParam(defaultValue = "10") int pageSize,
                                   @RequestParam(required = false) String username,
                                   @RequestParam(required = false) String nickname,
                                   @RequestParam(required = false) String phone) {
        Page<User> page = userService.list(current, pageSize, username, nickname, phone);
        return Result.success(page.getContent(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        return userService.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "用户不存在"));
    }

    @PostMapping
    public Result<User> create(@RequestBody User user) {
        try {
            return Result.success(userService.create(user));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @RequestBody User user) {
        try {
            return Result.success(userService.update(id, user));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        userService.disable(id);
        return Result.success(null);
    }

    @PutMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        userService.enable(id);
        return Result.success(null);
    }

    // ---- 用户-应用关联 ----
    @GetMapping("/{id}/apps")
    public Result<List<UserApp>> getUserApps(@PathVariable Long id) {
        return Result.success(userService.getUserApps(id));
    }

    @PutMapping("/{id}/apps")
    public Result<Void> saveUserApps(@PathVariable Long id, @RequestBody List<UserApp> apps) {
        userService.saveUserApps(id, apps);
        return Result.success(null);
    }

    // ---- 认证账户 ----
    @GetMapping("/{id}/auth-accounts")
    public Result<List<UserAuthAccount>> getAuthAccounts(@PathVariable Long id) {
        return Result.success(userService.getAuthAccounts(id));
    }

    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.length() < 6) {
            return Result.error(400, "密码长度至少6位");
        }
        userService.resetPassword(id, password);
        return Result.success(null);
    }

    @GetMapping("/{id}/roles")
    public Result<List<UserRole>> getUserRoles(@PathVariable Long id) {
        return Result.success(userService.getUserRoles(id));
    }

    @PutMapping("/{id}/roles")
    public Result<Void> setUserRoles(@PathVariable Long id,
                                     @RequestParam Long appId,
                                     @RequestParam(defaultValue = "0") Long createdBy,
                                     @RequestBody List<Long> roleIds) {
        userService.setUserRoles(id, appId, createdBy, roleIds);
        return Result.success(null);
    }
}

