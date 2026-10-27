package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwFilterTemplate;

import java.util.List;
import java.util.Optional;

@Repository
public interface GwFilterTemplateRepository extends CrudRepository<GwFilterTemplate, Long> {
    List<GwFilterTemplate> findByStatus(Integer status);

    Optional<GwFilterTemplate> findByFilterName(String filterName);
}
