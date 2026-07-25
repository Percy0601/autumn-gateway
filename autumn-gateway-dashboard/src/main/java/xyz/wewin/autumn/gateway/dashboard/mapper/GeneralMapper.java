package xyz.wewin.autumn.gateway.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xyz.wewin.autumn.gateway.dashboard.dto.UserRelationRole;

import java.util.List;

/**
 * @author: baoxin.zhao
 * @date: 2024/7/24
 */
@Mapper
public interface GeneralMapper {

    List<UserRelationRole> findRelationRoles(@Param("appId")Long appId,
                                             @Param("userId") Long userId,
                                             @Param("current") int current,
                                             @Param("pageSize")int pageSize);

    long countRelationRoles(@Param("appId")Long appId,
                            @Param("userId") Long userId,
                            @Param("current") int current,
                            @Param("pageSize")int pageSize);
}
