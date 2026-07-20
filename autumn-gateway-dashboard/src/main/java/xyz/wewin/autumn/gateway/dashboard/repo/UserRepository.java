package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends CrudRepository<User, Long> {

    @Query("""
        SELECT u.* FROM user u
        WHERE (:username IS NULL OR u.username LIKE '%' || :username || '%')
          AND (:nickname IS NULL OR u.nickname LIKE '%' || :nickname || '%')
          AND (:phone IS NULL OR u.phone LIKE '%' || :phone || '%')
        ORDER BY u.id DESC
        """)
    Page<User> findWithPage(@Param("username") String username,
                            @Param("nickname") String nickname,
                            @Param("phone") String phone,
                            Pageable pageable);

    Optional<User> findByUsername(String username);
}
