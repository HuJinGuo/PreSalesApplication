package com.bidcollab.repository;

import com.bidcollab.entity.UserMenu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMenuRepository extends JpaRepository<UserMenu, Long> {
  List<UserMenu> findByUserId(Long userId);

  void deleteByUserId(Long userId);

  void deleteByMenuId(Long menuId);
}
