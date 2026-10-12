package xyz.wewin.autumn.gateway.dashboard.service.basic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.wewin.autumn.gateway.dashboard.entity.basic.BasicMenu;
import xyz.wewin.autumn.gateway.dashboard.repo.BasicMenuRepository;
import xyz.wewin.autumn.gateway.dashboard.security.RequestContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 菜单服务，基于 basic_menu。
 *
 * <p>basic_menu 的 create_user / update_user 同样为 NOT NULL，从 {@link RequestContext} 取值。
 */
@Service
@Transactional
public class BasicMenuService {

    @Autowired
    private BasicMenuRepository repository;

    public Page<BasicMenu> list(Long appId, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), size);
        long offset = (long) Math.max(current - 1, 0) * size;
        List<BasicMenu> content = repository.findWithPage(appId, null, null, offset, (long) size);
        long total = repository.countWithFilter(appId, null, null);
        return new PageImpl<>(content, pageable, total);
    }

    /** 按应用返回全量菜单（前端据此构建树） */
    public List<BasicMenu> listByApp(Long appId) {
        return appId == null ? (List<BasicMenu>) repository.findAll() : repository.findByAppId(appId);
    }

    public Optional<BasicMenu> getById(Long id) {
        return repository.findById(id);
    }

    public BasicMenu create(BasicMenu menu) {
        if (menu.getMenuName() == null || menu.getMenuName().isBlank()) {
            throw new IllegalArgumentException("菜单名称不能为空");
        }
        menu.setId(null);
        // menu_url / menu_identification / menu_icon / remark 在库里都是 NOT NULL DEFAULT ''，
        // 前端省略空字段时为 null，必须兜底，否则插入会触发非空约束冲突
        if (menu.getMenuUrl() == null) menu.setMenuUrl("");
        if (menu.getMenuIdentification() == null) menu.setMenuIdentification("");
        if (menu.getMenuIcon() == null) menu.setMenuIcon("");
        if (menu.getRemark() == null) menu.setRemark("");
        if (menu.getParentId() == null) menu.setParentId(0L);
        if (menu.getMenuStatus() == null) menu.setMenuStatus(1);
        if (menu.getMenuType() == null) menu.setMenuType(2);
        if (menu.getSortNum() == null) menu.setSortNum(0);
        if (menu.getDelFlag() == null) menu.setDelFlag(0);
        menu.setCreateUser(RequestContext.currentUserId());
        menu.setUpdateUser(RequestContext.currentUserId());
        menu.setCreateTime(LocalDateTime.now());
        menu.setUpdateTime(LocalDateTime.now());
        return repository.save(menu);
    }

    public BasicMenu update(Long id, BasicMenu req) {
        BasicMenu existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜单不存在: " + id));
        if (req.getAppId() != null) existing.setAppId(req.getAppId());
        if (req.getParentId() != null) existing.setParentId(req.getParentId());
        if (req.getMenuType() != null) existing.setMenuType(req.getMenuType());
        if (req.getMenuName() != null) existing.setMenuName(req.getMenuName());
        if (req.getMenuUrl() != null) existing.setMenuUrl(req.getMenuUrl());
        if (req.getMenuIdentification() != null) existing.setMenuIdentification(req.getMenuIdentification());
        if (req.getMenuIcon() != null) existing.setMenuIcon(req.getMenuIcon());
        if (req.getSortNum() != null) existing.setSortNum(req.getSortNum());
        if (req.getMenuStatus() != null) existing.setMenuStatus(req.getMenuStatus());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        existing.setUpdateUser(RequestContext.currentUserId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }

    /** menuStatus: 1=启用 2=停用 */
    public BasicMenu updateStatus(Long id, Integer menuStatus) {
        BasicMenu existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜单不存在: " + id));
        existing.setMenuStatus(menuStatus);
        existing.setUpdateUser(RequestContext.currentUserId());
        existing.setUpdateTime(LocalDateTime.now());
        return repository.save(existing);
    }
}
