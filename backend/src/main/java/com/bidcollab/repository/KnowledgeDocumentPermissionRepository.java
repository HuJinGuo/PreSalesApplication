package com.bidcollab.repository;

import com.bidcollab.entity.KnowledgeDocumentPermission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentPermissionRepository extends JpaRepository<KnowledgeDocumentPermission, Long> {
  Optional<KnowledgeDocumentPermission> findByKnowledgeDocumentIdAndUserId(Long knowledgeDocumentId, Long userId);
  List<KnowledgeDocumentPermission> findByKnowledgeDocumentId(Long knowledgeDocumentId);
  List<KnowledgeDocumentPermission> findByUserId(Long userId);
  void deleteByKnowledgeDocumentId(Long knowledgeDocumentId);
}
