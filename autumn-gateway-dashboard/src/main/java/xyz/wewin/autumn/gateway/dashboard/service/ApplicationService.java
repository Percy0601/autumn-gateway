package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.Application;
import xyz.wewin.autumn.gateway.dashboard.repo.ApplicationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ApplicationService {

    @Autowired
    private ApplicationRepository repository;

    /**
     * 分页查询应用列表
     * @param page   ProTable 传入的 current（从1开始）
     * @param size   pageSize
     * @return Page<Application>
     */
    public Page<Application> list(int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return repository.findWithPage(pageable);
    }

    /**
     * 根据 ID 查询单个应用
     */
    public Optional<Application> getById(Long id) {
        return repository.findById(id);
    }

    /**
     * 新增应用
     */
    public Application create(Application app) {
        // 校验 appid 唯一
        if (repository.countByAppid(app.getAppid()) > 0) {
            throw new IllegalArgumentException("应用标识已存在: " + app.getAppid());
        }
        app.setId(null); // 确保新增
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        return repository.save(app);
    }

    /**
     * 更新应用（全量更新，忽略 null 字段）
     */
    public Application update(Long id, Application req) {
        Application existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("应用不存在: " + id));

        // 更新字段（只更新非 null 的字段，可根据需求调整）
        if (req.getAppid() != null) existing.setAppid(req.getAppid());
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getBasePath() != null) existing.setBasePath(req.getBasePath());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }

    /**
     * 获取所有应用（用于下拉选择）
     */
    public List<Application> listAll() {
        return (List<Application>) repository.findAll();
    }
}
