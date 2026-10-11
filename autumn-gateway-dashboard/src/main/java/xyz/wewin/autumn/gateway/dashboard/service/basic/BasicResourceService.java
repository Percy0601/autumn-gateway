package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicApp;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicResource;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicAppRepository;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicResourceRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 资源（权限原子项）服务，基于 basic_resource。
 */
@Service
@Transactional
public class BasicResourceService {

    @Autowired
    private BasicResourceRepository repository;

    @Autowired
    private BasicAppRepository appRepository;

    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    public Page<BasicResource> list(Long appId, String name, String httpMethod, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<BasicResource> content =
                repository.findWithPage(appId, nvl(name), nvl(httpMethod), offset, (long) size);
        long total = repository.countWithFilter(appId, nvl(name), nvl(httpMethod));
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<BasicResource> getById(Long id) {
        return repository.findById(id);
    }

    public BasicResource create(BasicResource res) {
        if (res.getAppId() == null) {
            throw new IllegalArgumentException("所属应用不能为空");
        }
        if (res.getName() == null || res.getName().isBlank()) {
            throw new IllegalArgumentException("资源名称不能为空");
        }
        res.setId(null);
        // 冗余一份 app_code，便于按应用编码检索
        if (res.getAppCode() == null || res.getAppCode().isBlank()) {
            res.setAppCode(appRepository.findById(res.getAppId()).map(BasicApp::getAppCode).orElse(""));
        }
        if (res.getHttpMethod() == null) res.setHttpMethod("ALL");
        if (res.getMatchType() == null) res.setMatchType("exact");
        if (res.getStatus() == null) res.setStatus(1);
        res.setCreatedAt(LocalDateTime.now());
        res.setUpdatedAt(LocalDateTime.now());
        return repository.save(res);
    }

    public BasicResource update(Long id, BasicResource req) {
        BasicResource existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资源不存在: " + id));
        if (req.getAppId() != null) existing.setAppId(req.getAppId());
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getPath() != null) existing.setPath(req.getPath());
        if (req.getHttpMethod() != null) existing.setHttpMethod(req.getHttpMethod());
        if (req.getMatchType() != null) existing.setMatchType(req.getMatchType());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (existing.getAppId() != null) {
            existing.setAppCode(appRepository.findById(existing.getAppId()).map(BasicApp::getAppCode).orElse(""));
        }
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    /** status: 1=正常 0=禁用 */
    public BasicResource updateStatus(Long id, Integer status) {
        BasicResource existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资源不存在: " + id));
        existing.setStatus(status);
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    public List<BasicResource> listAll() {
        return (List<BasicResource>) repository.findAll();
    }
}
