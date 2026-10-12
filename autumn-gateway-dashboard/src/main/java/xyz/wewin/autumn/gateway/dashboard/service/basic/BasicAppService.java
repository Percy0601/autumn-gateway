package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicApp;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicAppRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 应用服务，基于 basic_app。
 */
@Service
@Transactional
public class BasicAppService {

    @Autowired
    private BasicAppRepository repository;

    /** 空串哨兵：过滤参数为空时不参与 WHERE */
    private static String nvl(String v) {
        return v == null ? "" : v.trim();
    }

    public Page<BasicApp> list(String appCode, String name, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<BasicApp> content = repository.findWithPage(nvl(appCode), nvl(name), offset, (long) size);
        long total = repository.countWithFilter(nvl(appCode), nvl(name));
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<BasicApp> getById(Long id) {
        return repository.findById(id);
    }

    public BasicApp create(BasicApp app) {
        if (app.getAppCode() == null || app.getAppCode().isBlank()) {
            throw new IllegalArgumentException("应用标识不能为空");
        }
        if (repository.countByAppCode(app.getAppCode()) > 0) {
            throw new IllegalArgumentException("应用标识已存在: " + app.getAppCode());
        }
        app.setId(null);
        // name 在库里是 NOT NULL（无默认值），前端省略时兜底
        if (app.getName() == null) app.setName("");
        if (app.getStatus() == null) app.setStatus(1);
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        return repository.save(app);
    }

    public BasicApp update(Long id, BasicApp req) {
        BasicApp existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("应用不存在: " + id));
        if (req.getAppCode() != null) existing.setAppCode(req.getAppCode());
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getBasePath() != null) existing.setBasePath(req.getBasePath());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    /** status: 1=正常 0=禁用 */
    public BasicApp updateStatus(Long id, Integer status) {
        BasicApp existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("应用不存在: " + id));
        existing.setStatus(status);
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    public List<BasicApp> listAll() {
        return (List<BasicApp>) repository.findAll();
    }
}
