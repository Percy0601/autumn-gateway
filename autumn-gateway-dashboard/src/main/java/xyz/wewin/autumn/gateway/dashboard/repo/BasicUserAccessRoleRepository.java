package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUserAccessRole;

import java.util.List;
import java.util.Optional;

@Repository
public interface BasicUserAccessRoleRepository extends CrudRepository<BasicUserAccessRole, Long> {

    List<BasicUserAccessRole> findByUserId(Long userId);

    Optional<BasicUserAccessRole> findByEmpId(String empId);

    List<BasicUserAccessRole> findByUserIdAndLatestRoleId(Long userId, Long latestRoleId);
}
