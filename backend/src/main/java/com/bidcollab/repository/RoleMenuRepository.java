package com.bidcollab.repository;

import com.bidcollab.entity.RoleMenu;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleMenuRepository extends JpaRepository<RoleMenu, Long> {
  List<RoleMenu> findByRoleId(Long roleId);

  List<RoleMenu> findByRoleIdIn(Collection<Long> roleIds);

  void deleteByRoleId(Long roleId);

  void deleteByMenuId(Long menuId);
}
