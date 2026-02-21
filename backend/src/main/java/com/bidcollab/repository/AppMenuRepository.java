package com.bidcollab.repository;

import com.bidcollab.entity.AppMenu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppMenuRepository extends JpaRepository<AppMenu, Long> {
  List<AppMenu> findAllByOrderBySortIndexAscIdAsc();
}
