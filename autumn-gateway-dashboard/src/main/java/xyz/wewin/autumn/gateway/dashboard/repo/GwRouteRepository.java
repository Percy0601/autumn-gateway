package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwRoute;

import java.util.List;
import java.util.Optional;

@Repository
public interface GwRouteRepository extends CrudRepository<GwRoute, Long> {

    @Query("SELECT * FROM gw_route WHERE del_flag = 0 ORDER BY order_num, id")
    List<GwRoute> findAllActive();

    Optional<GwRoute> findByRouteCode(String routeCode);

    @Query("SELECT COUNT(1) FROM gw_route WHERE route_code = :routeCode AND del_flag = 0")
    long countByRouteCode(@Param("routeCode") String routeCode);

    @Query("SELECT COUNT(1) FROM gw_route WHERE route_code = :routeCode AND id <> :id AND del_flag = 0")
    long countByIdNotAndRouteCode(@Param("id") Long id, @Param("routeCode") String routeCode);
}
