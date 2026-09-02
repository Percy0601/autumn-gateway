package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.dto.UserRelationRole;
import xyz.wewin.autumn.gateway.dashboard.entity.UserRole;

import java.util.List;
import java.util.Optional;

// UserRoleRepository.java
@Repository
public interface UserRoleRepository extends CrudRepository<UserRole, Long> {

    @Query("""
        SELECT ur.* FROM user_role ur
        WHERE (:userId IS NULL OR ur.user_id = :userId)
        """)
    List<UserRole> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("INSERT INTO user_role(user_id, role_id, app_id, created_by, created_at) VALUES (:userId, :roleId, :appId, :createdBy, NOW())")
    void insert(@Param("userId") Long userId, @Param("roleId") Long roleId, @Param("appId") Long appId, @Param("createdBy") Long createdBy);

    @Query("""
        SELECT ur.* FROM user_role ur
        WHERE (:roleId IS NULL OR ur.role_id = :roleId)
        """)
    Optional<UserRole> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);

    @Query("""
        SELECT count(1) FROM user_role ur
        WHERE (:appId IS NULL OR ur.app_id = :appId)
          AND (:userId IS NULL OR ur.user_id= :userId)
        """)
    long countByAppIdAndUserId(Long appId, Long userId);

    @Query("""
        SELECT ur.* FROM user_role ur
        WHERE (:appId IS NULL OR ur.app_id = :appId)
          AND (:userId IS NULL OR ur.user_id= :userId)
        ORDER BY id DESC
        LIMIT :limit, :offset
        """)
    List<UserRole> findByAppIdAndUserId(@Param("appId") Long appId,
                                                @Param("userId") Long userId,
                                                @Param("limit")  long limit,
                                                @Param("offset") long offset);
}