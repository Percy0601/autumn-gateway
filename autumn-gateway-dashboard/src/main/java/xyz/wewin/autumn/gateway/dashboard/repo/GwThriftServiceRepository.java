package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwThriftService;

import java.util.List;
import java.util.Optional;

@Repository
public interface GwThriftServiceRepository extends CrudRepository<GwThriftService, Long> {

    @Query("SELECT * FROM gw_thrift_service WHERE del_flag = 0 ORDER BY id")
    List<GwThriftService> findAllActive();

    @Query("SELECT * FROM gw_thrift_service WHERE del_flag = 0 AND status = 1 ORDER BY id")
    List<GwThriftService> findAllEnabled();

    Optional<GwThriftService> findByServiceCode(String serviceCode);
}
