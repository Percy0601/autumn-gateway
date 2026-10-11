package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicGroup;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRoleGroup;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRoleGroupRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRoleRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 用户组服务，基于 basic_group，并维护 basic_role_group（用户组-角色关联）。
 */
@Service
@Transactional
public class BasicGroupService {

    @Autowired
    private BasicGroupRepository repository;

    @Autowired
    private BasicRoleGroupRepository roleGroupRepository;

    @Autowired
    private BasicRoleRepository roleRepository;

    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    public Page<BasicGroup> list(String name, String code, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<BasicGroup> content = repository.findWithPage(nvl(name), nvl(code), offset, (long) size);
        long total = repository.countWithFilter(nvl(name), nvl(code));
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<BasicGroup> getById(Long id) {
        return repository.findById(id);
    }

    public BasicGroup create(BasicGroup group) {
        if (group.getName() == null || group.getName().isBlank()) {
            throw new IllegalArgumentException("岗位名称不能为空");
        }
        group.setId(null);
        if (group.getDeleted() == null) group.setDeleted(0);
        if (group.getCode() == null) group.setCode("");
        if (group.getOrgId() == null) group.setOrgId(0L);
        if (group.getJobId() == null) group.setJobId(0L);
        group.setCreateTime(LocalDateTime.now());
        group.setUpdateTime(LocalDateTime.now());
        return repository.save(group);
    }

    public BasicGroup update(Long id, BasicGroup req) {
        BasicGroup existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户组不存在: " + id));
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getCode() != null) existing.setCode(req.getCode());
        if (req.getOrgId() != null) existing.setOrgId(req.getOrgId());
        if (req.getJobId() != null) existing.setJobId(req.getJobId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    /** 软删除：deleted = 1 */
    public void remove(Long id) {
        BasicGroup existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户组不存在: " + id));
        existing.setDeleted(1);
        existing.setUpdateTime(LocalDateTime.now());
        repository.save(existing);
    }

    public List<BasicGroup> listAll() {
        return (List<BasicGroup>) repository.findAll();
    }

    // ---------- 用户组 - 角色关联（basic_role_group） ----------

    /** 查询用户组已关联的角色 */
    public List<BasicRole> getRoles(Long groupId) {
        List<Long> roleIds = roleGroupRepository.findByGroupId(groupId).stream()
                .filter(rg -> rg.getDeleted() == null || rg.getDeleted() == 0)
                .map(BasicRoleGroup::getRoleId)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return (List<BasicRole>) roleRepository.findAllById(roleIds);
    }

    /** 全量覆盖用户组的角色关联 */
    public void setRoles(Long groupId, List<Long> roleIds) {
        if (!repository.existsById(groupId)) {
            throw new IllegalArgumentException("用户组不存在: " + groupId);
        }
        roleGroupRepository.findByGroupId(groupId).forEach(roleGroupRepository::delete);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : roleIds) {
            if (roleId == null) continue;
            BasicRoleGroup rg = new BasicRoleGroup();
            rg.setId(null);
            rg.setGroupId(groupId);
            rg.setRoleId(roleId);
            rg.setDeleted(0);
            rg.setCreateTime(now);
            rg.setUpdateTime(now);
            roleGroupRepository.save(rg);
        }
    }
}
