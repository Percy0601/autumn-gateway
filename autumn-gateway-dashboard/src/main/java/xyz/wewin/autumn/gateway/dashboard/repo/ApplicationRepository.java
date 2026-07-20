package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.Application;

@Repository
public interface ApplicationRepository extends CrudRepository<Application, Long> {

    // 分页查询（需引入 Pageable）
    // 或者使用 JdbcTemplate 自定义查询
    @Query("""
        SELECT * FROM application
        ORDER BY id DESC
        """)
    Page<Application> findWithPage(Pageable pageable);
    @Query("""
        SELECT COUNT(1) FROM application
        WHERE appid = :appid
        ORDER BY id DESC
        """)
    int countByAppid(String appid);
}