package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicNamespace;

import java.util.List;
import java.util.Optional;

@Repository
public interface BasicNamespaceRepository extends CrudRepository<BasicNamespace, Long> {

    Optional<BasicNamespace> findByCode(String code);

    @Query("SELECT COUNT(1) FROM basic_namespace WHERE code = :code")
    long countByCode(String code);

    @Query("SELECT COUNT(1) FROM basic_namespace WHERE name = :name")
    long countByName(String name);

    /** 更新场景：排除自身后，是否已有相同 code */
    @Query("SELECT COUNT(1) FROM basic_namespace WHERE id <> :id AND code = :code")
    long countByIdNotAndCode(@Param("id") Long id, @Param("code") String code);

    /** 更新场景：排除自身后，是否已有相同 name */
    @Query("SELECT COUNT(1) FROM basic_namespace WHERE id <> :id AND name = :name")
    long countByIdNotAndName(@Param("id") Long id, @Param("name") String name);

    @Query("""
        SELECT n.* FROM basic_namespace n
        WHERE (:code IS NULL OR :code = '' OR n.code LIKE CONCAT('%', :code, '%'))
          AND (:name IS NULL OR :name = '' OR n.name LIKE CONCAT('%', :name, '%'))
        ORDER BY n.id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<BasicNamespace> findWithPage(@Param("code") String code,
                                      @Param("name") String name,
                                      @Param("limit") long limit,
                                      @Param("offset") long offset);

    @Query("""
        SELECT COUNT(1) FROM basic_namespace
        WHERE (:code IS NULL OR :code = '' OR code LIKE CONCAT('%', :code, '%'))
          AND (:name IS NULL OR :name = '' OR name LIKE CONCAT('%', :name, '%'))
        """)
    long countWithFilter(String code, String name);
}
