package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;

import java.util.Optional;

@Repository
public interface BasicUserRepository extends CrudRepository<BasicUser, Long> {

    Optional<BasicUser> findByEmpId(String empId);
}
