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

    Optional<BasicUser> findByEmpNo(String empNo);

    /**
     * 分页查询基础用户。
     * 说明：字符串过滤统一用空串 '' 作为"不过滤"哨兵，避免 NULL 参数在
     * MySQL / PostgreSQL 下的类型推断差异。
     */
    @Query("""
        SELECT u.* FROM basic_user u
        WHERE (:empNo = '' OR u.emp_no LIKE CONCAT('%', :empNo, '%'))
        ORDER BY u.id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<BasicUser> findWithPage(@Param("empNo") String empNo,
                                 @Param("offset") long offset,
                                 @Param("limit") long limit);

    @Query("""
        SELECT COUNT(1) FROM basic_user
        WHERE (:empNo = '' OR emp_no LIKE CONCAT('%', :empNo, '%'))
        """)
    long countWithFilter(@Param("empNo") String empNo);
}
