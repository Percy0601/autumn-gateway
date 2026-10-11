package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicResource;

import java.util.List;
import java.util.Optional;

@Repository
public interface BasicResourceRepository extends CrudRepository<BasicResource, Long> {

    List<BasicResource> findByAppId(Long appId);

    List<BasicResource> findByAppCode(String appCode);

    Optional<BasicResource> findByAppIdAndName(Long appId, String name);

    @Query("""
        SELECT r.* FROM basic_resource r
        WHERE (:appId IS NULL OR :appId = '' OR r.app_id = :appId)
          AND (:name IS NULL OR :name = '' OR r.name LIKE CONCAT('%', :name, '%'))
          AND (:httpMethod IS NULL OR :httpMethod = '' OR r.http_method = :httpMethod)
        ORDER BY r.id DESC
        LIMIT :offset OFFSET :limit
        """)
    List<BasicResource> findWithPage(@Param("appId") Long appId,
                                     @Param("name") String name,
                                     @Param("httpMethod") String httpMethod,
                                     @Param("limit") long limit,
                                     @Param("offset") long offset);

    @Query("""
        SELECT COUNT(1) FROM basic_resource
        WHERE (:appId IS NULL OR :appId = '' OR app_id = :appId)
          AND (:name IS NULL OR :name = '' OR name LIKE CONCAT('%', :name, '%'))
          AND (:httpMethod IS NULL OR :httpMethod = '' OR http_method = :httpMethod)
        """)
    long countWithFilter(Long appId, String name, String httpMethod);
}
