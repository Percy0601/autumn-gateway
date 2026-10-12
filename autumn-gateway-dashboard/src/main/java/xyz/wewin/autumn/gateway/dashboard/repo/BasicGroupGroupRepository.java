package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicGroupGroup;

import java.util.List;

@Repository
public interface BasicGroupGroupRepository extends CrudRepository<BasicGroupGroup, Long> {

    /** 查某个上级组直属的下级组 */
    List<BasicGroupGroup> findByParentGroupId(Long parentGroupId);

    /** 查某个下级组直属的上级组 */
    List<BasicGroupGroup> findByGroupId(Long groupId);

    List<BasicGroupGroup> findByParentGroupIdAndGroupId(Long parentGroupId, Long groupId);
}
