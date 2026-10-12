package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUserRole;

import java.util.List;

@Repository
public interface BasicUserRoleRepository extends CrudRepository<BasicUserRole, Long> {

    List<BasicUserRole> findByUserId(Long userId);

    List<BasicUserRole> findByRoleId(Long roleId);

    List<BasicUserRole> findByAppId(Long appId);

    List<BasicUserRole> findByAppIdAndUserId(Long appId, Long userId);

    List<BasicUserRole> findByUserIdAndRoleId(Long userId, Long roleId);
}
