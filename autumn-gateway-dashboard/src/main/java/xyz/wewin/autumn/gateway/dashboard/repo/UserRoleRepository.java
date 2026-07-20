package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.UserRole;

import java.util.List;

// UserRoleRepository.java
@Repository
public interface UserRoleRepository extends CrudRepository<UserRole, Long> {
    List<UserRole> findByUserId(Long userId);
    void deleteByUserId(Long userId);
    @Modifying
    @Query("INSERT INTO user_role(user_id, role_id, app_id, created_by, created_at) VALUES (:userId, :roleId, :appId, :createdBy, NOW())")
    void insert(@Param("userId") Long userId, @Param("roleId") Long roleId, @Param("appId") Long appId, @Param("createdBy") Long createdBy);
}