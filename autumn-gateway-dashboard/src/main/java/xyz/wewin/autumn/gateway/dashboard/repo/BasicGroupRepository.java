package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicGroup;

import java.util.List;

@Repository
public interface BasicGroupRepository extends CrudRepository<BasicGroup, Long> {

    List<BasicGroup> findByName(String name);

    List<BasicGroup> findByOrgId(Long orgId);

    List<BasicGroup> findByDeleted(Integer deleted);
}
