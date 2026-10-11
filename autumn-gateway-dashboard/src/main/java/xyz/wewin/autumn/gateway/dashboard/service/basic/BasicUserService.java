package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicGroup;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUserGroup;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicUserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 基础用户服务，基于 basic_user，并维护 basic_user_group（用户-用户组关联）。
 */
@Service
@Transactional
public class BasicUserService {

    @Autowired
    private BasicUserRepository repository;

    @Autowired
    private BasicUserGroupRepository userGroupRepository;

    @Autowired
    private BasicGroupRepository groupRepository;

    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    public Page<BasicUser> list(String empNo, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<BasicUser> content = repository.findWithPage(nvl(empNo), offset, (long) size);
        long total = repository.countWithFilter(nvl(empNo));
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<BasicUser> getById(Long id) {
        return repository.findById(id);
    }

    public BasicUser create(BasicUser user) {
        if (user.getEmpNo() == null || user.getEmpNo().isBlank()) {
            throw new IllegalArgumentException("员工工号不能为空");
        }
        if (repository.findByEmpNo(user.getEmpNo()).isPresent()) {
            throw new IllegalArgumentException("员工工号已存在: " + user.getEmpNo());
        }
        user.setId(null);
        if (user.getAdmin() == null) user.setAdmin(0);
        if (user.getLatestRoleId() == null) user.setLatestRoleId(0L);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return repository.save(user);
    }

    public BasicUser update(Long id, BasicUser req) {
        BasicUser existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        if (req.getEmpNo() != null) existing.setEmpNo(req.getEmpNo());
        if (req.getAdmin() != null) existing.setAdmin(req.getAdmin());
        if (req.getLatestRoleId() != null) existing.setLatestRoleId(req.getLatestRoleId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    public void remove(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("用户不存在: " + id);
        }
        // 先清理关联，避免留下孤儿数据
        userGroupRepository.findByUserId(id).forEach(userGroupRepository::delete);
        repository.deleteById(id);
    }

    // ---------- 用户 - 用户组关联（basic_user_group） ----------

    /** 查询用户已关联的用户组 */
    public List<BasicGroup> getGroups(Long userId) {
        List<Long> groupIds = userGroupRepository.findByUserId(userId).stream()
                .filter(ug -> ug.getDeleted() == null || ug.getDeleted() == 0)
                .map(BasicUserGroup::getGroupId)
                .distinct()
                .toList();
        if (groupIds.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicGroup>) groupRepository.findAllById(groupIds);
    }

    /** 全量覆盖用户的用户组关联 */
    public void setGroups(Long userId, List<Long> groupIds) {
        if (!repository.existsById(userId)) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        userGroupRepository.findByUserId(userId).forEach(userGroupRepository::delete);
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long groupId : groupIds) {
            if (groupId == null) continue;
            BasicUserGroup ug = new BasicUserGroup();
            ug.setId(null);
            ug.setUserId(userId);
            ug.setGroupId(groupId);
            ug.setDeleted(0);
            ug.setCreateTime(now);
            ug.setUpdateTime(now);
            userGroupRepository.save(ug);
        }
    }
}
