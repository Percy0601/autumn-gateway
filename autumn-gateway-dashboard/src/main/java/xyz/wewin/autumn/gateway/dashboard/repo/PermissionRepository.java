package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.Permission;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends CrudRepository<Permission, Long> {

    @Query("""
        SELECT p.* FROM permission p
        WHERE (:appId IS NULL OR p.app_id = :appId)
          AND (:code IS NULL OR p.code LIKE '%' || :code || '%')
          AND (:name IS NULL OR p.name LIKE '%' || :name || '%')
          AND (:permType IS NULL OR p.perm_type = :permType)
        ORDER BY p.id DESC
        """)
    List<Permission> findWithPage(@Param("appId") Long appId,
                                  @Param("code") String code,
                                  @Param("name") String name,
                                  @Param("permType") String permType,
                                  Pageable pageable);

    List<Permission> findByAppId(Long appId);
    Optional<Permission> findByAppIdAndCode(Long appId, String code);
    @Query("""
        SELECT COUNT(1) FROM permission p
        WHERE (:appId IS NULL OR p.app_id = :appId)
          AND (:code IS NULL OR p.code LIKE '%' || :code || '%')
          AND (:name IS NULL OR p.name LIKE '%' || :name || '%')
          AND (:permType IS NULL OR p.perm_type = :permType)
        """)
    long countWithFilter(Long appId, String code, String name, String permType);
}
