package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenu;

import java.util.List;

@Repository
public interface BasicMenuRepository extends CrudRepository<BasicMenu, Long> {

    List<BasicMenu> findByAppId(Long appId);

    List<BasicMenu> findByParentId(Long parentId);

    /** boundIds：已关联的菜单 id，不受过滤条件影响且排在最前；未传时用 {-1} 占位 */
    @Query("""
        SELECT m.* FROM basic_menu m
        WHERE ( ( :appId IS NULL OR m.app_id = :appId )
            AND ( :menuName IS NULL OR :menuName = '' OR m.menu_name LIKE CONCAT('%', :menuName, '%') )
            AND ( :menuType IS NULL OR m.menu_type = :menuType ) )
           OR m.id IN (:boundIds)
        ORDER BY CASE WHEN m.id IN (:boundIds) THEN 0 ELSE 1 END, m.id DESC
        LIMIT :offset OFFSET :limit
        """)
    List<BasicMenu> findWithPage(@Param("appId") Long appId,
                                 @Param("menuName") String menuName,
                                 @Param("menuType") Integer menuType,
                                 @Param("boundIds") List<Long> boundIds,
                                 @Param("limit") long limit,
                                 @Param("offset") long offset);

    @Query("""
        SELECT COUNT(1) FROM basic_menu
        WHERE ( ( :appId IS NULL OR app_id = :appId )
            AND ( :menuName IS NULL OR :menuName = '' OR menu_name LIKE CONCAT('%', :menuName, '%') )
            AND ( :menuType IS NULL OR menu_type = :menuType ) )
           OR id IN (:boundIds)
        """)
    long countWithFilter(@Param("appId") Long appId,
                         @Param("menuName") String menuName,
                         @Param("menuType") Integer menuType,
                         @Param("boundIds") List<Long> boundIds);
}
