package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwTplField;

import java.util.List;

/**
 * 模板配置项仓储（v2）。
 */
public interface GwTplFieldRepository extends CrudRepository<GwTplField, Long> {

    List<GwTplField> findByTplIdOrderBySortNum(Long tplId);

    long deleteByTplId(Long tplId);
}
