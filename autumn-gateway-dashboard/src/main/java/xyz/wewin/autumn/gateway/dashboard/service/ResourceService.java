package xyz.wewin.autumn.gateway.dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.Resource;
import xyz.wewin.autumn.gateway.dashboard.repo.ResourceRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class ResourceService {

    @Autowired
    private ResourceRepository resourceRepository;

    public Page<Resource> list(int page, int size, Long appId, String resType, String matchType, String name) {
        return resourceRepository.findWithPage(appId, resType, matchType, name, PageRequest.of(page - 1, size));
    }

    public Optional<Resource> getById(Long id) {
        return resourceRepository.findById(id);
    }

    public Resource create(Resource resource) {
        resource.setId(null);
        resource.setCreatedAt(LocalDateTime.now());
        resource.setUpdatedAt(LocalDateTime.now());
        return resourceRepository.save(resource);
    }

    public Resource update(Long id, Resource req) {
        Resource existing = resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        if (req.getAppId() != null) existing.setAppId(req.getAppId());
        if (req.getParentId() != null) existing.setParentId(req.getParentId());
        if (req.getResType() != null) existing.setResType(req.getResType());
        if (req.getMatchType() != null) existing.setMatchType(req.getMatchType());
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getAction() != null) existing.setAction(req.getAction());
        if (req.getIcon() != null) existing.setIcon(req.getIcon());
        if (req.getSort() != null) existing.setSort(req.getSort());
        if (req.getHidden() != null) existing.setHidden(req.getHidden());
        existing.setUpdatedAt(LocalDateTime.now());
        return resourceRepository.save(existing);
    }

    public void delete(Long id) {
        // 检查是否有子资源
        if (!resourceRepository.findByParentId(id).isEmpty()) {
            throw new IllegalArgumentException("该资源下存在子资源，请先删除子资源");
        }
        resourceRepository.deleteById(id);
    }
}
