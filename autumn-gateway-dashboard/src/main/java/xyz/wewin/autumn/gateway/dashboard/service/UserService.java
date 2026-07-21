package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.*;
import xyz.wewin.autumn.gateway.dashboard.repo.UserAppRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.UserAuthAccountRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.UserRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.UserRoleRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAppRepository userAppRepository;
    @Autowired
    private UserAuthAccountRepository authAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    public Page<User> list(int page, int size, String username, String nickname, String phone) {
        List<User> content = userRepository.findWithPage(username, nickname, phone, PageRequest.of(page - 1, size));
        long total = userRepository.countWithFilter(username, nickname, phone);
        return new PageImpl<>(content, PageRequest.of(page - 1, size), total);
    }

    public Optional<User> getById(Long id) {
        return userRepository.findById(id);
    }

    public User create(User user) {
        if (user.getUsername() != null && userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        user.setId(null);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public User update(Long id, User req) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (req.getNickname() != null) existing.setNickname(req.getNickname());
        if (req.getEmail() != null) existing.setEmail(req.getEmail());
        if (req.getPhone() != null) existing.setPhone(req.getPhone());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        // username 一般不修改，如需修改需唯一性校验
        existing.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(existing);
    }

    public void disable(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setStatus(0);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void enable(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setStatus(1);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    // ---- 用户-应用关联 ----
    public List<UserApp> getUserApps(Long userId) {
        return userAppRepository.findByUserId(userId);
    }

    public void saveUserApps(Long userId, List<UserApp> apps) {
        userAppRepository.deleteByUserId(userId);
        apps.forEach(app -> {
            app.setUserId(userId);
            app.setCreatedAt(LocalDateTime.now());
            userAppRepository.save(app);
        });
    }

    // ---- 认证账户（只读 + 重置密码） ----
    public List<UserAuthAccount> getAuthAccounts(Long userId) {
        return authAccountRepository.findByUserId(userId);
    }

    public void resetPassword(Long userId, String newPassword) {
        // 对密码进行 BCrypt 加密
        String encodedPassword = new BCryptPasswordEncoder().encode(newPassword);
        authAccountRepository.updatePassword(userId, encodedPassword);
    }

    /**
     * 获取用户的角色ID列表（按应用分组，或直接返回所有）
     */
    public List<UserRole> getUserRoles(Long userId) {
        return userRoleRepository.findByUserId(userId);
    }

    /**
     * 设置用户的角色（全量覆盖，需要指定应用和授权人）
     */
    public void setUserRoles(Long userId, Long appId, Long createdBy, List<Long> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        roleIds.forEach(roleId -> userRoleRepository.insert(userId, roleId, appId, createdBy));
    }
}
