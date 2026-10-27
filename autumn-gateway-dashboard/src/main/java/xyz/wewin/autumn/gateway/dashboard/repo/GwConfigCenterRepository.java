package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigCenter;

import java.util.List;

@Repository
public interface GwConfigCenterRepository extends CrudRepository<GwConfigCenter, Long> {
    List<GwConfigCenter> findAllByStatus(Integer status);
}
