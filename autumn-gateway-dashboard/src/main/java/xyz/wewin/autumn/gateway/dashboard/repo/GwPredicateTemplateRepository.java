package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwPredicateTemplate;

import java.util.List;

@Repository
public interface GwPredicateTemplateRepository extends CrudRepository<GwPredicateTemplate, Long> {
    List<GwPredicateTemplate> findByStatus(Integer status);
}
