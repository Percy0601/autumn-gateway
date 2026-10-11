package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenu;

import java.util.List;

@Repository
public interface BasicMenuRepository extends CrudRepository<BasicMenu, Long> {

    List<BasicMenu> findByAppId(Integer appId);

    List<BasicMenu> findByParentId(Long parentId);

    @Query("""
        SELECT m.* FROM basic_menu m
        WHERE (:appId IS NULL OR m.app_id = :appId)
          AND (:menuName IS NULL OR :menuName = '' OR m.menu_name LIKE CONCAT('%', :menuName, '%'))
          AND (:menuType IS NULL OR m.menu_type = :menuType)
        ORDER BY m.id DESC
        LIMIT :offset OFFSET :limit
        """)
    List<BasicMenu> findWithPage(@Param("appId") Integer appId,
                                 @Param("menuName") String menuName,
                                 @Param("menuType") Integer menuType,
                                 @Param("limit") long limit,
                                 @Param("offset") long offset);

    @Query("""
        SELECT COUNT(1) FROM basic_menu
        WHERE (:appId IS NULL OR app_id = :appId)
          AND (:menuName IS NULL OR :menuName = '' OR menu_name LIKE CONCAT('%', :menuName, '%'))
          AND (:menuType IS NULL OR menu_type = :menuType)
        """)
    long countWithFilter(Integer appId, String menuName, Integer menuType);
}
