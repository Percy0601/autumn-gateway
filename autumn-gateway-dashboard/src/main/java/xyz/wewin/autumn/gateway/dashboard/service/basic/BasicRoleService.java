package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicRoleRepository;
import xyz.wewin.autumn.gateway.dashboard.security.RequestContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 角色服务，基于 basic_role。
 *
 * <p>basic_role 的 create_user / update_user 为 NOT NULL，
 * 这里统一从 {@link RequestContext}（由 JWT 解析得到）取当前操作人。
 */
@Service
@Transactional
public class BasicRoleService {

    @Autowired
    private BasicRoleRepository repository;

    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    public Page<BasicRole> list(Long appId, String code, String name, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<BasicRole> content = repository.findWithPage(appId, nvl(code), nvl(name), offset, (long) size);
        long total = repository.countWithFilter(appId, nvl(code), nvl(name));
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<BasicRole> getById(Long id) {
        return repository.findById(id);
    }

    public BasicRole create(BasicRole role) {
        if (role.getCode() == null || role.getCode().isBlank()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        if (role.getAppId() == null) {
            throw new IllegalArgumentException("所属应用不能为空");
        }
        role.setId(null);
        if (role.getStatus() == null) role.setStatus(1);
        if (role.getType() == null) role.setType(2);
        if (role.getMaster() == null) role.setMaster(2);
        if (role.getDelFlag() == null) role.setDelFlag(0);
        role.setCreateUser(RequestContext.currentUserId());
        role.setUpdateUser(RequestContext.currentUserId());
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        return repository.save(role);
    }

    public BasicRole update(Long id, BasicRole req) {
        BasicRole existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));
        if (req.getAppId() != null) existing.setAppId(req.getAppId());
        if (req.getCode() != null) existing.setCode(req.getCode());
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getType() != null) existing.setType(req.getType());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getMaster() != null) existing.setMaster(req.getMaster());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        existing.setUpdateUser(RequestContext.currentUserId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    /** status: 1=启用 2=停用 */
    public BasicRole updateStatus(Long id, Integer status) {
        BasicRole existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));
        existing.setStatus(status);
        existing.setUpdateUser(RequestContext.currentUserId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    public List<BasicRole> listAll() {
        return (List<BasicRole>) repository.findAll();
    }
}
