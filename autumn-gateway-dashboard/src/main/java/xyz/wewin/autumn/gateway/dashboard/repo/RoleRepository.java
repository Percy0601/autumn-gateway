package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.Role;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends CrudRepository<Role, Long> {

    @Query("""
        SELECT r.* FROM role r
        WHERE (:appId IS NULL OR r.app_id = :appId)
          AND (:code IS NULL OR r.code LIKE '%' || :code || '%')
          AND (:name IS NULL OR r.name LIKE '%' || :name || '%')
        ORDER BY r.id DESC
        LIMIT :limit, :offset
        """)
    List<Role> findWithPage(@Param("appId") Long appId,
                            @Param("code") String code,
                            @Param("name") String name,
                            @Param("limit")long limit,
                            @Param("offset")long offset);

    List<Role> findByAppId(Long appId);

    Optional<Role> findByAppIdAndCode(Long appId, String code);
    @Query("""
        SELECT COUNT(1) FROM role r
        WHERE (:appId IS NULL OR r.app_id = :appId)
          AND (:code IS NULL OR r.code LIKE '%' || :code || '%')
          AND (:name IS NULL OR r.name LIKE '%' || :name || '%')
        ORDER BY r.id DESC
        """)
    long countWithFilter(Long appId, String code, String name);
}
