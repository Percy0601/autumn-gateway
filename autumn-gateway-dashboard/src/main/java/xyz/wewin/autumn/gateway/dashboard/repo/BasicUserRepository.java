package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface BasicUserRepository extends CrudRepository<BasicUser, Long> {

    /** 员工工号唯一性校验 */
    Optional<BasicUser> findByEmpNo(String empNo);

    /** 登录名唯一性校验 */
    Optional<BasicUser> findByUsername(String username);

    /**
     * 分页查询基础用户。
     * 说明：字符串过滤统一用空串 '' 作为"不过滤"哨兵，状态用 -1 作为"不过滤"哨兵，
     * 避免 NULL 参数在 MySQL / PostgreSQL 下的类型推断差异。
     */
    @Query("""
        SELECT u.* FROM basic_user u
        WHERE (:empNo = '' OR u.emp_no LIKE CONCAT('%', :empNo, '%'))
          AND (:username = '' OR u.username LIKE CONCAT('%', :username, '%'))
          AND (:phone = '' OR u.phone LIKE CONCAT('%', :phone, '%'))
          AND (:status = -1 OR u.status = :status)
        ORDER BY u.id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<BasicUser> findWithPage(@Param("empNo") String empNo,
                                 @Param("username") String username,
                                 @Param("phone") String phone,
                                 @Param("status") Integer status,
                                 @Param("offset") long offset,
                                 @Param("limit") long limit);

    @Query("""
        SELECT COUNT(1) FROM basic_user
        WHERE (:empNo = '' OR emp_no LIKE CONCAT('%', :empNo, '%'))
          AND (:username = '' OR username LIKE CONCAT('%', :username, '%'))
          AND (:phone = '' OR phone LIKE CONCAT('%', :phone, '%'))
          AND (:status = -1 OR status = :status)
        """)
    long countWithFilter(@Param("empNo") String empNo,
                         @Param("username") String username,
                         @Param("phone") String phone,
                         @Param("status") Integer status);
}
