package com.bidcollab.repository;

import com.bidcollab.entity.Section;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {
  List<Section> findByDocumentIdOrderBySortIndexAsc(Long documentId);
  List<Section> findByParentIdOrderBySortIndexAsc(Long parentId);
}
