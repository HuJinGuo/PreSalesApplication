package com.bidcollab.repository;

import com.bidcollab.entity.SectionTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionTemplateRepository extends JpaRepository<SectionTemplate, Long> {
  List<SectionTemplate> findAllByOrderByCreatedAtDesc();
}

