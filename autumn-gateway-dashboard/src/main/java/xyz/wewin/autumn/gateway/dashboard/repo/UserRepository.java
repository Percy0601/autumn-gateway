package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends CrudRepository<User, Long> {

    @Query("""
        SELECT u.* FROM user u
        WHERE (:username IS NULL OR u.username LIKE CONCAT('%', :username, '%'))
          AND (:nickname IS NULL OR u.nickname LIKE CONCAT('%', :nickname, '%'))
          AND (:phone IS NULL OR u.phone LIKE CONCAT('%', :phone, '%'))
        ORDER BY u.id DESC
        LIMIT :limit, :offset
        """)
    List<User> findWithPage(@Param("username") String username,
                            @Param("nickname") String nickname,
                            @Param("phone") String phone,
                            @Param("limit")long limit,
                            @Param("offset")long offset);

    Optional<User> findByUsername(String username);

    @Query("""
        SELECT COUNT(1) FROM user u
        WHERE (:username IS NULL OR u.username LIKE '%' || :username || '%')
          AND (:nickname IS NULL OR u.nickname LIKE '%' || :nickname || '%')
          AND (:phone IS NULL OR u.phone LIKE '%' || :phone || '%')
        ORDER BY u.id DESC
        """)
    long countWithFilter(String username, String nickname, String phone);
}
