package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwGlobalFilter;

import java.util.List;

@Repository
public interface GwGlobalFilterRepository extends CrudRepository<GwGlobalFilter, Long> {

    @Query("SELECT * FROM gw_global_filter WHERE del_flag = 0 ORDER BY sort_num, id")
    List<GwGlobalFilter> findAllActive();
}
