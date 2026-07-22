package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.UserApp;

import java.util.List;

@Repository
public interface UserAppRepository extends CrudRepository<UserApp, Long> {
    @Query("""
        SELECT ua.* FROM user_app ua
        WHERE (:userId IS NULL OR ua.user_id = :userId)
        """)
    List<UserApp> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM user_app WHERE user_id = :userId")
    void deleteByUserId(Long userId);
    boolean existsByUserIdAndAppId(Long userId, Long appId);
}
