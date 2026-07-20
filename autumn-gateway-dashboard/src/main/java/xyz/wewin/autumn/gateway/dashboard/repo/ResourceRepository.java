package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.Resource;

import java.util.List;

@Repository
public interface ResourceRepository extends CrudRepository<Resource, Long> {

    @Query("""
        SELECT r.* FROM resource r
        WHERE (:appId IS NULL OR r.app_id = :appId)
          AND (:resType IS NULL OR r.res_type = :resType)
          AND (:matchType IS NULL OR r.match_type = :matchType)
          AND (:name IS NULL OR r.name LIKE '%' || :name || '%')
        ORDER BY r.sort ASC, r.id DESC
        """)
    Page<Resource> findWithPage(@Param("appId") Long appId,
                                @Param("resType") String resType,
                                @Param("matchType") String matchType,
                                @Param("name") String name,
                                Pageable pageable);

    List<Resource> findByAppId(Long appId);
    List<Resource> findByParentId(Long parentId);
    List<Resource> findByResTypeAndAppId(String resType, Long appId);
}
