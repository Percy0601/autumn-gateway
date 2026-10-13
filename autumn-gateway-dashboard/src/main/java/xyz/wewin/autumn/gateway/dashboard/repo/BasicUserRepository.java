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
    /** boundIds：已关联的用户 id，不受过滤条件影响且排在最前；未传时用 {-1} 占位 */
    @Query("""
        SELECT u.* FROM basic_user u
        WHERE ( ( :empNo = '' OR u.emp_no LIKE CONCAT('%', :empNo, '%') )
            AND ( :username = '' OR u.username LIKE CONCAT('%', :username, '%') )
            AND ( :phone = '' OR u.phone LIKE CONCAT('%', :phone, '%') )
            AND ( :status = -1 OR u.status = :status ) )
           OR u.id IN (:boundIds)
        ORDER BY CASE WHEN u.id IN (:boundIds) THEN 0 ELSE 1 END, u.id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<BasicUser> findWithPage(@Param("empNo") String empNo,
                                 @Param("username") String username,
                                 @Param("phone") String phone,
                                 @Param("status") Integer status,
                                 @Param("boundIds") List<Long> boundIds,
                                 @Param("offset") long offset,
                                 @Param("limit") long limit);

    @Query("""
        SELECT COUNT(1) FROM basic_user
        WHERE ( ( :empNo = '' OR emp_no LIKE CONCAT('%', :empNo, '%') )
            AND ( :username = '' OR username LIKE CONCAT('%', :username, '%') )
            AND ( :phone = '' OR phone LIKE CONCAT('%', :phone, '%') )
            AND ( :status = -1 OR status = :status ) )
           OR id IN (:boundIds)
        """)
    long countWithFilter(@Param("empNo") String empNo,
                         @Param("username") String username,
                         @Param("phone") String phone,
                         @Param("status") Integer status,
                         @Param("boundIds") List<Long> boundIds);
}
