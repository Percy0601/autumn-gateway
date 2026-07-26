package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;
import xyz.wewin.autumn.gateway.dashboard.dto.UserRelationRole;
import xyz.wewin.autumn.gateway.dashboard.entity.*;
import xyz.wewin.autumn.gateway.dashboard.mapper.GeneralMapper;
import xyz.wewin.autumn.gateway.dashboard.repo.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private GeneralMapper generalMapper;

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
    public void setUserRoles(Long userId, Long createdBy, List<Long> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        roleIds.forEach(roleId -> {
            Role role = roleRepository.findById(roleId)
                    .orElse(null);
            if(Objects.isNull(role)) {
                return;
            }
            Long appId = role.getAppId();

            userRoleRepository.insert(userId, roleId, appId, createdBy);
        });
    }


    /**
     * 1. 更新用户关联的应用（先删后插）
     */
    public void updateUserApps(Long userId, List<Long> appIds) {
        userAppRepository.deleteByUserId(userId);
        if (appIds != null && !appIds.isEmpty()) {
            List<UserApp> list = appIds.stream()
                    .map(appId -> {
                        UserApp ua = new UserApp();
                        ua.setUserId(userId);
                        ua.setAppId(appId);
                        ua.setCreatedAt(LocalDateTime.now());
                        return ua;
                    })
                    .collect(Collectors.toList());
            userAppRepository.saveAll(list);
        }
    }

    /**
     * 2. 更新用户关联的角色（先删后插）
     */
    public void updateUserRoles(Long userId, List<Long> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            List<UserRole> list = roleIds.stream()
                    .map(roleId -> {
                        UserRole ur = new UserRole();
                        ur.setUserId(userId);
                        ur.setRoleId(roleId);
                        ur.setCreatedAt(LocalDateTime.now());
                        return ur;
                    })
                    .collect(Collectors.toList());
            userRoleRepository.saveAll(list);
        }
    }

    /**
     * 3. 查询该用户下所有角色（返回角色对象列表）
     */
    public List<Role> listRolesByUserId(Long userId) {
        List<Long> roleIds = userRoleRepository.findByUserId(userId)
                .stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());
        if (roleIds.isEmpty())
            return Collections.emptyList();
        List<Role> roles = null;
        roleRepository.findAllById(roleIds)
                .forEach(roles::add);
        return roles;
    }

    public Page<UserRelationRole> findRelationRoles(Long appId,
                                                    Long userId,
                                                    int current,
                                                    int pageSize) {
        long total = generalMapper.countRelationRoles(appId, userId, current, pageSize);
        List<UserRelationRole> content = generalMapper.findRelationRoles(appId, userId, current, pageSize);
        content.forEach(it -> {
            if(null == it.getUserId()) {
                it.setRelationStatus(0);
            } else {
                it.setRelationStatus(1);
            }
            Application application = applicationRepository.findById(it.getAppId()).orElse(null);
            if (application != null) {
                it.setAppName(application.getName());
            }
        });
        return new PageImpl<>(content, PageRequest.of(current - 1, pageSize), total);
    }

    public List<User> listAll() {
        List<User> users = new ArrayList<>();
        userRepository.findAll().forEach(users::add);
        return users;
    }
}
