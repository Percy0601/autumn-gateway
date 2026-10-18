package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicNamespace;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicNamespaceRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 命名空间（租户）服务，基于 basic_namespace。
 */
@Service
@Transactional
public class BasicNamespaceService {

    @Autowired
    private BasicNamespaceRepository repository;

    /** 空串哨兵：过滤参数为空时不参与 WHERE */
    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    public Page<BasicNamespace> list(String code, String name, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<BasicNamespace> content = repository.findWithPage(nvl(code), nvl(name), (long) size, offset);
        long total = repository.countWithFilter(nvl(code), nvl(name));
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<BasicNamespace> getById(Long id) {
        return repository.findById(id);
    }

    public Optional<BasicNamespace> findByCode(String code) {
        return repository.findByCode(code);
    }

    public BasicNamespace create(BasicNamespace ns) {
        if (ns.getCode() == null || ns.getCode().isBlank()) {
            throw new IllegalArgumentException("命名空间编码不能为空");
        }
        if (ns.getName() == null || ns.getName().isBlank()) {
            throw new IllegalArgumentException("命名空间名称不能为空");
        }
        if (repository.countByCode(ns.getCode()) > 0) {
            throw new IllegalArgumentException("命名空间编码已存在: " + ns.getCode());
        }
        if (repository.countByName(ns.getName()) > 0) {
            throw new IllegalArgumentException("命名空间名称已存在: " + ns.getName());
        }
        ns.setId(null);
        if (ns.getDescription() == null) ns.setDescription("");
        if (ns.getStatus() == null) ns.setStatus(1);
        ns.setCreatedAt(LocalDateTime.now());
        ns.setUpdatedAt(LocalDateTime.now());
        return repository.save(ns);
    }

    public BasicNamespace update(Long id, BasicNamespace req) {
        BasicNamespace existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("命名空间不存在: " + id));
        if (req.getCode() != null) {
            if (!req.getCode().equals(existing.getCode())
                    && repository.countByIdNotAndCode(id, req.getCode()) > 0) {
                throw new IllegalArgumentException("命名空间编码已存在: " + req.getCode());
            }
            existing.setCode(req.getCode());
        }
        if (req.getName() != null) {
            if (!req.getName().equals(existing.getName())
                    && repository.countByIdNotAndName(id, req.getName()) > 0) {
                throw new IllegalArgumentException("命名空间名称已存在: " + req.getName());
            }
            existing.setName(req.getName());
        }
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    /** status: 1=正常 0=禁用 */
    public BasicNamespace updateStatus(Long id, Integer status) {
        BasicNamespace existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("命名空间不存在: " + id));
        existing.setStatus(status);
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    public List<BasicNamespace> listAll() {
        return (List<BasicNamespace>) repository.findAll();
    }
}
