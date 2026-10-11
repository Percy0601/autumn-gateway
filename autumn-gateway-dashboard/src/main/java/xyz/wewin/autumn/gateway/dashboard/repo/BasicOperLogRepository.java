package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicOperLog;

import java.util.List;

@Repository
public interface BasicOperLogRepository extends CrudRepository<BasicOperLog, Long> {

    List<BasicOperLog> findByOperId(Long operId);

    @Query("""
        SELECT l.* FROM basic_oper_log l
        WHERE (:appCode IS NULL OR :appCode = '' OR l.app_code = :appCode)
          AND (:title IS NULL OR :title = '' OR l.title LIKE CONCAT('%', :title, '%'))
          AND (:businessType IS NULL OR :businessType = '' OR l.business_type = :businessType)
        ORDER BY l.op_time DESC
        LIMIT :offset OFFSET :limit
        """)
    List<BasicOperLog> findWithPage(@Param("appCode") String appCode,
                                    @Param("title") String title,
                                    @Param("businessType") String businessType,
                                    @Param("limit") long limit,
                                    @Param("offset") long offset);

    @Query("""
        SELECT COUNT(1) FROM basic_oper_log
        WHERE (:appCode IS NULL OR :appCode = '' OR app_code = :appCode)
          AND (:title IS NULL OR :title = '' OR title LIKE CONCAT('%', :title, '%'))
          AND (:businessType IS NULL OR :businessType = '' OR business_type = :businessType)
        """)
    long countWithFilter(String appCode, String title, String businessType);
}
