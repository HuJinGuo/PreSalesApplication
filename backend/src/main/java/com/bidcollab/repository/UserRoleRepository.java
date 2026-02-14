package com.bidcollab.repository;

import com.bidcollab.entity.UserRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
  List<UserRole> findByUserId(Long userId);
}
