package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwValueType;

import java.util.List;
import java.util.Optional;

/**
 * 值类型目录仓储（v2）。
 */
public interface GwValueTypeRepository extends CrudRepository<GwValueType, Long> {

    List<GwValueType> findByStatusOrderBySortNum(Integer status);

    List<GwValueType> findAllByOrderByKindAscSortNumAsc();

    Optional<GwValueType> findByName(String name);

    long countByName(String name);
}
