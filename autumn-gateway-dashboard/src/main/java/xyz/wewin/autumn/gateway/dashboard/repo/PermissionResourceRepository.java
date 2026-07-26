package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.PermissionResource;

import java.util.List;

@Repository
public interface PermissionResourceRepository extends CrudRepository<PermissionResource, Long> {
    List<PermissionResource> findByPermissionId(Long permissionId);
    void deleteByPermissionId(Long permissionId);
    @Modifying
    @Query("INSERT INTO permission_resource(permission_id, resource_id) VALUES (:permissionId, :resourceId)")
    void insert(@Param("permissionId") Long permissionId, @Param("resourceId") Long resourceId);
}
