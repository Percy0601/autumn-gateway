package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.RolePermission;

import java.util.List;

// RolePermissionRepository.java
@Repository
public interface RolePermissionRepository extends CrudRepository<RolePermission, Long> {
    List<RolePermission> findByRoleId(Long roleId);
    void deleteByRoleId(Long roleId);
    @Modifying
    @Query("INSERT INTO role_permission(role_id, permission_id) VALUES (:roleId, :permissionId)")
    void insert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
