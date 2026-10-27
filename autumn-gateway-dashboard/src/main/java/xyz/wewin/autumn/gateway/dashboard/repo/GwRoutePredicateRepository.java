package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwRoutePredicate;

import java.util.List;

@Repository
public interface GwRoutePredicateRepository extends CrudRepository<GwRoutePredicate, Long> {

    @Query("SELECT * FROM gw_route_predicate WHERE route_id = :routeId AND del_flag = 0 ORDER BY sort_num, id")
    List<GwRoutePredicate> findByRouteId(@Param("routeId") Long routeId);
}
