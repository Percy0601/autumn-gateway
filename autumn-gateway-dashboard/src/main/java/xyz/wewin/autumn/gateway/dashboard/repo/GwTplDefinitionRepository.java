package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwTplDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 模板定义仓储（v2）。
 */
public interface GwTplDefinitionRepository extends CrudRepository<GwTplDefinition, Long> {

    List<GwTplDefinition> findByKindOrderBySortNum(String kind);

    List<GwTplDefinition> findByKindAndStatusOrderBySortNum(String kind, Integer status);

    List<GwTplDefinition> findAllByOrderByKindAscSortNumAsc();

    Optional<GwTplDefinition> findByKindAndName(String kind, String name);

    long countByKindAndName(String kind, String name);

    long countByKindAndNameAndIdNot(String kind, String name, Long id);
}
