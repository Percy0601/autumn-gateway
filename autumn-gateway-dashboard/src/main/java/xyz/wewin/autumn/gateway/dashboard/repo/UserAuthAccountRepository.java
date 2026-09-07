package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.UserAuthAccount;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAuthAccountRepository extends CrudRepository<UserAuthAccount, Long> {

    List<UserAuthAccount> findByUserId(Long userId);

    Optional<UserAuthAccount> findByUserIdAndIdentityType(Long userId, String identityType);

    // 重置密码时用到
    @Modifying
    @Query("UPDATE user_auth_account SET credential = :credential, updated_at = NOW() WHERE user_id = :userId AND identity_type = 'password'")
    void updatePassword(@Param("userId") Long userId, @Param("credential") String credential);
}
