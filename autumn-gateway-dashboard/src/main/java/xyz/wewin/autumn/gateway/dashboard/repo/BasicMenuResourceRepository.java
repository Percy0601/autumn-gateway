package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenuResource;

import java.util.List;

@Repository
public interface BasicMenuResourceRepository extends CrudRepository<BasicMenuResource, Long> {

    List<BasicMenuResource> findByMenuId(Long menuId);

    List<BasicMenuResource> findByResourceId(Long resourceId);

    List<BasicMenuResource> findByMenuIdAndResourceId(Long menuId, Long resourceId);
}
