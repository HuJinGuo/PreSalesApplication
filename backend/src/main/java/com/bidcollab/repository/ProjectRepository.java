package com.bidcollab.repository;

import com.bidcollab.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
  boolean existsByCode(String code);
}
