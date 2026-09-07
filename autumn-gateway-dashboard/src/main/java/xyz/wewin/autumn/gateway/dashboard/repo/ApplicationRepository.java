package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.Application;

import java.util.List;

@Repository
public interface ApplicationRepository extends CrudRepository<Application, Long> {

    @Query("""
        SELECT app.* FROM application app
        WHERE (:appId IS NULL OR :appId = '' OR app.appid = :appId)
          AND (:name IS NULL OR :name = '' OR app.name LIKE CONCAT('%', :name, '%'))
        ORDER BY id DESC
        LIMIT :limit, :offset
        """)
    List<Application> findWithPage(@Param("appId")String appId,
                                   @Param("name")String name,
                                   @Param("limit")long limit,
                                   @Param("offset")long offset);

    @Query("SELECT COUNT(1) FROM application")
    long countAll();

    @Query("""
        SELECT COUNT(1) FROM application
        WHERE appid = :appid
        """)
    int countByAppid(String appid);
    @Query("""
        SELECT count(1) FROM application app
        WHERE (:appId IS NULL OR :appId = '' OR app.appid = :appId)
          AND (:name IS NULL OR :name = '' OR app.name LIKE CONCAT('%', :name, '%'))
        ORDER BY id DESC
        """)
    long countByFilter(String appId, String name);
}