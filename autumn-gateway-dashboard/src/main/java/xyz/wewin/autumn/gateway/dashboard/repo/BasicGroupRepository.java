package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicGroup;

import java.util.List;

@Repository
public interface BasicGroupRepository extends CrudRepository<BasicGroup, Long> {

    List<BasicGroup> findByName(String name);

    List<BasicGroup> findByOrgId(Long orgId);

    List<BasicGroup> findByDeleted(Integer deleted);

    List<BasicGroup> findByNamespaceId(Long namespaceId);

    long countByNamespaceIdAndName(Long namespaceId, String name);

    long countByIdNotAndNamespaceIdAndName(Long id, Long namespaceId, String name);

    /**
     * 分页查询用户组（只查未删除 deleted=0）。
     * 字符串过滤用空串 '' 作为"不过滤"哨兵；namespaceId 传 null 表示不限命名空间。
     */
    @Query("""
        SELECT g.* FROM basic_group g
        WHERE g.deleted = 0
          AND (:namespaceId IS NULL OR g.namespace_id = :namespaceId)
          AND (:name = '' OR g.name LIKE CONCAT('%', :name, '%'))
          AND (:code = '' OR g.code LIKE CONCAT('%', :code, '%'))
        ORDER BY g.id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<BasicGroup> findWithPage(@Param("namespaceId") Long namespaceId,
                                  @Param("name") String name,
                                  @Param("code") String code,
                                  @Param("offset") long offset,
                                  @Param("limit") long limit);

    @Query("""
        SELECT COUNT(1) FROM basic_group
        WHERE deleted = 0
          AND (:namespaceId IS NULL OR namespace_id = :namespaceId)
          AND (:name = '' OR name LIKE CONCAT('%', :name, '%'))
          AND (:code = '' OR code LIKE CONCAT('%', :code, '%'))
        """)
    long countWithFilter(@Param("namespaceId") Long namespaceId,
                         @Param("name") String name,
                         @Param("code") String code);
}
