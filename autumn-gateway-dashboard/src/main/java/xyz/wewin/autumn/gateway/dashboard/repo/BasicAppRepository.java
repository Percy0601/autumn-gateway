package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicApp;

import java.util.List;
import java.util.Optional;

@Repository
public interface BasicAppRepository extends CrudRepository<BasicApp, Long> {

    Optional<BasicApp> findByAppCode(String appCode);

    @Query("SELECT COUNT(1) FROM basic_app WHERE app_code = :appCode")
    int countByAppCode(String appCode);

    @Query("""
        SELECT a.* FROM basic_app a
        WHERE (:appCode IS NULL OR :appCode = '' OR a.app_code = :appCode)
          AND (:name IS NULL OR :name = '' OR a.name LIKE CONCAT('%', :name, '%'))
        ORDER BY a.id DESC
        LIMIT :offset OFFSET :limit
        """)
    List<BasicApp> findWithPage(@Param("appCode") String appCode,
                                @Param("name") String name,
                                @Param("limit") long limit,
                                @Param("offset") long offset);

    @Query("""
        SELECT COUNT(1) FROM basic_app
        WHERE (:appCode IS NULL OR :appCode = '' OR app_code = :appCode)
          AND (:name IS NULL OR :name = '' OR name LIKE CONCAT('%', :name, '%'))
        """)
    long countWithFilter(String appCode, String name);
}
