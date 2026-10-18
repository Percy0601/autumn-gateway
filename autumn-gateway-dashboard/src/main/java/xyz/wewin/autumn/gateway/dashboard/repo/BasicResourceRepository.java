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

    /** boundIds：已关联的资源 id，不受过滤条件影响且排在最前；未传时用 {-1} 占位 */
    @Query("""
        SELECT r.* FROM basic_resource r
        WHERE ( ( :appId IS NULL OR r.app_id = :appId )
            AND ( :name IS NULL OR :name = '' OR r.name LIKE CONCAT('%', :name, '%') )
            AND ( :httpMethod IS NULL OR :httpMethod = ''
                  OR r.http_method LIKE CONCAT('%', :httpMethod, '%') ) )
           OR r.id IN (:boundIds)
        ORDER BY CASE WHEN r.id IN (:boundIds) THEN 0 ELSE 1 END, r.id DESC
        LIMIT :offset OFFSET :limit
        """)
    List<BasicResource> findWithPage(@Param("appId") Long appId,
                                     @Param("name") String name,
                                     @Param("httpMethod") String httpMethod,
                                     @Param("boundIds") List<Long> boundIds,
                                     @Param("limit") long limit,
                                     @Param("offset") long offset);

    @Query("""
        SELECT COUNT(1) FROM basic_resource
        WHERE ( ( :appId IS NULL OR app_id = :appId )
            AND ( :name IS NULL OR :name = '' OR name LIKE CONCAT('%', :name, '%') )
            AND ( :httpMethod IS NULL OR :httpMethod = ''
                  OR http_method LIKE CONCAT('%', :httpMethod, '%') ) )
           OR id IN (:boundIds)
        """)
    long countWithFilter(@Param("appId") Long appId,
                         @Param("name") String name,
                         @Param("httpMethod") String httpMethod,
                         @Param("boundIds") List<Long> boundIds);
}
