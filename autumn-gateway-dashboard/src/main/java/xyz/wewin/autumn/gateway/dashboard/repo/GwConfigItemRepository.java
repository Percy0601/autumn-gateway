package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigItem;

import java.util.List;

@Repository
public interface GwConfigItemRepository extends CrudRepository<GwConfigItem, Long> {

    @Query("SELECT * FROM gw_config_item WHERE profile_code = :profileCode ORDER BY config_group, sort_num, id")
    List<GwConfigItem> findByProfile(@Param("profileCode") String profileCode);

    @Query("SELECT COUNT(1) FROM gw_config_item WHERE profile_code = :profileCode AND config_key = :configKey AND id <> :id")
    long countByIdNotAndKey(@Param("id") Long id,
                            @Param("profileCode") String profileCode,
                            @Param("configKey") String configKey);
}
