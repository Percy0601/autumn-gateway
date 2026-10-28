package xyz.wewin.autumn.gateway.dashboard.repo;

import org.springframework.data.repository.CrudRepository;
import xyz.wewin.autumn.gateway.dashboard.entity.gateway.GwConfigStruct;

import java.util.List;

/**
 * 结构化全局配置实例仓储（v2）。
 */
public interface GwConfigStructRepository extends CrudRepository<GwConfigStruct, Long> {

    List<GwConfigStruct> findByProfileCodeAndDelFlagOrderBySortNum(String profileCode, Integer delFlag);

    List<GwConfigStruct> findByDelFlagOrderBySortNum(Integer delFlag);

    long countByProfileCodeAndStructNameAndDelFlag(String profileCode, String structName, Integer delFlag);
}
