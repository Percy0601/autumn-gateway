package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.Application;

import java.util.List;

@Repository
public interface ApplicationRepository extends CrudRepository<Application, Long> {

    @Query("""
        SELECT * FROM application
        ORDER BY id DESC
        """)
    List<Application> findWithPage(Pageable pageable);

    @Query("SELECT COUNT(1) FROM application")
    long countAll();

    @Query("""
        SELECT COUNT(1) FROM application
        WHERE appid = :appid
        """)
    int countByAppid(String appid);
}