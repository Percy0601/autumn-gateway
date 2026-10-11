package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRoleGroup;

import java.util.List;

@Repository
public interface BasicRoleGroupRepository extends CrudRepository<BasicRoleGroup, Long> {

    List<BasicRoleGroup> findByRoleId(Long roleId);

    List<BasicRoleGroup> findByGroupId(Long groupId);

    List<BasicRoleGroup> findByRoleIdAndGroupId(Long roleId, Long groupId);
}
