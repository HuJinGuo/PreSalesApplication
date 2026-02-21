package com.bidcollab.repository;

import com.bidcollab.entity.SectionVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionVersionRepository extends JpaRepository<SectionVersion, Long> {
  List<SectionVersion> findBySectionIdOrderByCreatedAtDesc(Long sectionId);
  List<SectionVersion> findBySectionIdIn(List<Long> sectionIds);
  void deleteBySectionIdIn(List<Long> sectionIds);
}
