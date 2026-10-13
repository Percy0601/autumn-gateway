package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicRolePermission;

import java.util.List;

@Repository
public interface BasicRolePermissionRepository extends CrudRepository<BasicRolePermission, Long> {

    List<BasicRolePermission> findByRoleId(Long roleId);

    List<BasicRolePermission> findByRoleIdAndRefId(Long roleId, Long refId);

    List<BasicRolePermission> findByRoleIdAndType(Long roleId, Integer type);

    /** 反查：某个菜单/资源被哪些角色授权（配合 type 使用） */
    List<BasicRolePermission> findByRefIdAndType(Long refId, Integer type);
}
