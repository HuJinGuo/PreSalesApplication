package com.bidcollab.repository;

import com.bidcollab.entity.RolePermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
  List<RolePermission> findByRoleId(Long roleId);
}
