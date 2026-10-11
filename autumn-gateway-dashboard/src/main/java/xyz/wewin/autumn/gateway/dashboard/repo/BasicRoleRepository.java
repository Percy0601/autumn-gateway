package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;

import java.util.List;
import java.util.Optional;

@Repository
public interface BasicRoleRepository extends CrudRepository<BasicRole, Long> {

    List<BasicRole> findByAppId(Integer appId);

    List<BasicRole> findByCode(String code);

    Optional<BasicRole> findByAppIdAndCode(Integer appId, String code);

    @Query("""
        SELECT r.* FROM basic_role r
        WHERE (:appId IS NULL OR r.app_id = :appId)
          AND (:code IS NULL OR :code = '' OR r.code LIKE CONCAT('%', :code, '%'))
          AND (:name IS NULL OR :name = '' OR r.name LIKE CONCAT('%', :name, '%'))
        ORDER BY r.id DESC
        LIMIT :offset OFFSET :limit
        """)
    List<BasicRole> findWithPage(@Param("appId") Integer appId,
                                 @Param("code") String code,
                                 @Param("name") String name,
                                 @Param("limit") long limit,
                                 @Param("offset") long offset);

    @Query("""
        SELECT COUNT(1) FROM basic_role
        WHERE (:appId IS NULL OR app_id = :appId)
          AND (:code IS NULL OR :code = '' OR code LIKE CONCAT('%', :code, '%'))
          AND (:name IS NULL OR :name = '' OR name LIKE CONCAT('%', :name, '%'))
        """)
    long countWithFilter(Integer appId, String code, String name);
}
