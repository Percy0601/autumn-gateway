package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicOperLog;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BasicOperLogRepository extends CrudRepository<BasicOperLog, Long> {

    List<BasicOperLog> findByOperId(Long operId);

    /**
     * 分页查询操作日志（只读）。
     *
     * <p>参数约定：为避免 NULL 绑定在 MySQL / PostgreSQL 下的类型推断差异，
     * 全部使用哨兵值——字符串用 ''，状态用 -1，时间用 1970-01-01 / 9999-12-31。
     */
    @Query("""
        SELECT l.* FROM basic_oper_log l
        WHERE (:appCode = '' OR l.app_code = :appCode)
          AND (:title = '' OR l.title LIKE CONCAT('%', :title, '%'))
          AND (:businessType = '' OR l.business_type = :businessType)
          AND (:status = -1 OR l.status = :status)
          AND l.op_time >= :startTime
          AND l.op_time <= :endTime
        ORDER BY l.op_time DESC
        LIMIT :limit OFFSET :offset
        """)
    List<BasicOperLog> findWithPage(@Param("appCode") String appCode,
                                    @Param("title") String title,
                                    @Param("businessType") String businessType,
                                    @Param("status") Integer status,
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime,
                                    @Param("offset") long offset,
                                    @Param("limit") long limit);

    @Query("""
        SELECT COUNT(1) FROM basic_oper_log
        WHERE (:appCode = '' OR app_code = :appCode)
          AND (:title = '' OR title LIKE CONCAT('%', :title, '%'))
          AND (:businessType = '' OR business_type = :businessType)
          AND (:status = -1 OR status = :status)
          AND op_time >= :startTime
          AND op_time <= :endTime
        """)
    long countWithFilter(@Param("appCode") String appCode,
                         @Param("title") String title,
                         @Param("businessType") String businessType,
                         @Param("status") Integer status,
                         @Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);
}
