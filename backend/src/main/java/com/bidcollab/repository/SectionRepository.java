package com.bidcollab.repository;

import com.bidcollab.entity.Section;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SectionRepository extends JpaRepository<Section, Long> {
  @Query("""
      select s
      from Section s
      left join fetch s.currentVersion
      left join fetch s.parent
      where s.document.id = :documentId
      order by s.sortIndex asc
      """)
  List<Section> findTreeByDocumentId(@Param("documentId") Long documentId);
  List<Section> findByParentIdOrderBySortIndexAsc(Long parentId);

  @Query("""
      select s
      from Section s
      left join fetch s.currentVersion
      left join fetch s.parent
      where s.document.id = :documentId
      order by s.sortIndex asc
      """)
  List<Section> findForExportByDocumentId(@Param("documentId") Long documentId);

  @Modifying
  @Query("""
      update Section s
      set s.currentVersion = null
      where s.document.id = :documentId
      """)
  int clearCurrentVersionByDocumentId(@Param("documentId") Long documentId);
}
