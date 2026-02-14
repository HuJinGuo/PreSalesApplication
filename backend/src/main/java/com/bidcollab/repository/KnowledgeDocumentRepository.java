package com.bidcollab.repository;

import com.bidcollab.entity.KnowledgeDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
  List<KnowledgeDocument> findByKnowledgeBaseIdOrderByCreatedAtDesc(Long knowledgeBaseId);
  void deleteByKnowledgeBaseId(Long knowledgeBaseId);
}
