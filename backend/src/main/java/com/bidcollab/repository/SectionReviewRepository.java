package com.bidcollab.repository;

import com.bidcollab.entity.SectionReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionReviewRepository extends JpaRepository<SectionReview, Long> {
  List<SectionReview> findBySectionIdOrderByCreatedAtDesc(Long sectionId);
  void deleteBySectionIdIn(List<Long> sectionIds);
  void deleteByVersionIdIn(List<Long> versionIds);
}
