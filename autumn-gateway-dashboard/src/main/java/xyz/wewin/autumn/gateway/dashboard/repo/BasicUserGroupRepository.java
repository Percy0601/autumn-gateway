package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUserGroup;

import java.util.List;

@Repository
public interface BasicUserGroupRepository extends CrudRepository<BasicUserGroup, Long> {

    List<BasicUserGroup> findByUserId(Long userId);

    List<BasicUserGroup> findByGroupId(Long groupId);

    List<BasicUserGroup> findByUserIdAndGroupId(Long userId, Long groupId);
}
