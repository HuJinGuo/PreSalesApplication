package com.bidcollab.repository;

import com.bidcollab.entity.SectionChunkRef;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionChunkRefRepository extends JpaRepository<SectionChunkRef, Long> {
  List<SectionChunkRef> findBySectionIdOrderByParagraphIndexAscIdAsc(Long sectionId);
  void deleteBySectionId(Long sectionId);
  void deleteBySectionIdIn(List<Long> sectionIds);
}

