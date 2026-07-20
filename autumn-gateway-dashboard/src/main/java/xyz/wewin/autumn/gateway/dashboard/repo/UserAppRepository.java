package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.UserApp;

import java.util.List;

@Repository
public interface UserAppRepository extends CrudRepository<UserApp, Long> {
    List<UserApp> findByUserId(Long userId);
    void deleteByUserId(Long userId);
    boolean existsByUserIdAndAppId(Long userId, Long appId);
}
