package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwValueTypeField;

import java.util.List;

/**
 * 复合类型结构仓储（v2）。
 */
public interface GwValueTypeFieldRepository extends CrudRepository<GwValueTypeField, Long> {

    List<GwValueTypeField> findByTypeIdOrderBySortNum(Long typeId);

    long deleteByTypeId(Long typeId);
}
