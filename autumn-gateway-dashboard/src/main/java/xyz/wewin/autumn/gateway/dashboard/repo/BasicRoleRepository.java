package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRole;

import java.util.List;
import java.util.Optional;

@Repository
public interface BasicRoleRepository extends CrudRepository<BasicRole, Long> {

    List<BasicRole> findByNamespaceId(Long namespaceId);

    Optional<BasicRole> findByNamespaceIdAndCode(Long namespaceId, String code);

    /** 同一命名空间下，角色编码是否重复 */
    @Query("SELECT COUNT(1) FROM basic_role WHERE namespace_id = :namespaceId AND code = :code")
    long countByNamespaceIdAndCode(@Param("namespaceId") Long namespaceId, @Param("code") String code);

    /** 同一命名空间下，角色名称是否重复 */
    @Query("SELECT COUNT(1) FROM basic_role WHERE namespace_id = :namespaceId AND name = :name")
    long countByNamespaceIdAndName(@Param("namespaceId") Long namespaceId, @Param("name") String name);

    /** 更新场景：排除自身后，同一命名空间下是否已有相同 code */
    @Query("SELECT COUNT(1) FROM basic_role WHERE id <> :id AND namespace_id = :namespaceId AND code = :code")
    long countByIdNotAndNamespaceIdAndCode(@Param("id") Long id, @Param("namespaceId") Long namespaceId, @Param("code") String code);

    /** 更新场景：排除自身后，同一命名空间下是否已有相同 name */
    @Query("SELECT COUNT(1) FROM basic_role WHERE id <> :id AND namespace_id = :namespaceId AND name = :name")
    long countByIdNotAndNamespaceIdAndName(@Param("id") Long id, @Param("namespaceId") Long namespaceId, @Param("name") String name);

    @Query("""
        SELECT r.* FROM basic_role r
        WHERE (:namespaceId IS NULL OR r.namespace_id = :namespaceId)
          AND (:code IS NULL OR :code = '' OR r.code LIKE CONCAT('%', :code, '%'))
          AND (:name IS NULL OR :name = '' OR r.name LIKE CONCAT('%', :name, '%'))
        ORDER BY r.id DESC
        LIMIT :offset OFFSET :limit
        """)
    List<BasicRole> findWithPage(@Param("namespaceId") Long namespaceId,
                                 @Param("code") String code,
                                 @Param("name") String name,
                                 @Param("limit") long limit,
                                 @Param("offset") long offset);

    @Query("""
        SELECT COUNT(1) FROM basic_role
        WHERE (:namespaceId IS NULL OR namespace_id = :namespaceId)
          AND (:code IS NULL OR :code = '' OR code LIKE CONCAT('%', :code, '%'))
          AND (:name IS NULL OR :name = '' OR name LIKE CONCAT('%', :name, '%'))
        """)
    long countWithFilter(Long namespaceId, String code, String name);
}
