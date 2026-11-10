package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwServiceInstance;

import java.util.List;
import java.util.Optional;

public interface GwServiceInstanceRepository extends CrudRepository<GwServiceInstance, Long> {

    @Query("SELECT * FROM gw_service_instance WHERE del_flag = 0 ORDER BY id")
    List<GwServiceInstance> findAllActive();

    @Query("SELECT * FROM gw_service_instance WHERE del_flag = 0 AND status = 1 ORDER BY id")
    List<GwServiceInstance> findAllEnabled();

    @Query("SELECT * FROM gw_service_instance WHERE id = ? AND del_flag = 0")
    Optional<GwServiceInstance> findActiveById(Long id);
}
