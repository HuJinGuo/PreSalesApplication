package com.bidcollab.repository;

import com.bidcollab.entity.KnowledgeDocumentPermission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface KnowledgeDocumentPermissionRepository extends JpaRepository<KnowledgeDocumentPermission, Long> {
  Optional<KnowledgeDocumentPermission> findByKnowledgeDocumentIdAndUserId(Long knowledgeDocumentId, Long userId);
  List<KnowledgeDocumentPermission> findByKnowledgeDocumentId(Long knowledgeDocumentId);
  List<KnowledgeDocumentPermission> findByUserId(Long userId);
  void deleteByKnowledgeDocumentId(Long knowledgeDocumentId);

  @Modifying
  @Transactional
  @Query("""
      delete from KnowledgeDocumentPermission p
      where p.knowledgeDocumentId in (
        select d.id from KnowledgeDocument d where d.knowledgeBase.id = :knowledgeBaseId
      )
      """)
  void deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
}
