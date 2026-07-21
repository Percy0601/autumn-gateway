package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends CrudRepository<AuditLog, Long> {

    @Query("""
        SELECT a.* FROM audit_log a
        WHERE (:appId IS NULL OR a.app_id = :appId)
          AND (:userId IS NULL OR a.user_id = :userId)
          AND (:action IS NULL OR a.action = :action)
          AND (:status IS NULL OR a.status = :status)
          AND (:startTime IS NULL OR a.created_at >= :startTime)
          AND (:endTime IS NULL OR a.created_at <= :endTime)
        ORDER BY a.created_at DESC
        """)
    List<AuditLog> findWithPage(@Param("appId") Long appId,
                                @Param("userId") Long userId,
                                @Param("action") String action,
                                @Param("status") Integer status,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                Pageable pageable);
    @Query("""
        SELECT COUNT(1) FROM audit_log a
        WHERE (:appId IS NULL OR a.app_id = :appId)
          AND (:userId IS NULL OR a.user_id = :userId)
          AND (:action IS NULL OR a.action = :action)
          AND (:status IS NULL OR a.status = :status)
          AND (:startTime IS NULL OR a.created_at >= :startTime)
          AND (:endTime IS NULL OR a.created_at <= :endTime)
        """)
    long countWithFilter(@Param("appId") Long appId,
                         @Param("userId") Long userId,
                         @Param("action") String action,
                         @Param("status") Integer status,
                         @Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);
}
