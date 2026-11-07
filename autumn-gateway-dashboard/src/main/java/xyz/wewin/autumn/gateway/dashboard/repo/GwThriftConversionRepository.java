package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwThriftConversion;

import java.util.List;
import java.util.Optional;

@Repository
public interface GwThriftConversionRepository extends CrudRepository<GwThriftConversion, Long> {

    @Query("SELECT * FROM gw_thrift_conversion WHERE del_flag = 0 ORDER BY id")
    List<GwThriftConversion> findAllActive();

    @Query("SELECT * FROM gw_thrift_conversion WHERE del_flag = 0 AND status = 1 ORDER BY id")
    List<GwThriftConversion> findAllEnabled();

    Optional<GwThriftConversion> findByConversionCode(String conversionCode);
}
