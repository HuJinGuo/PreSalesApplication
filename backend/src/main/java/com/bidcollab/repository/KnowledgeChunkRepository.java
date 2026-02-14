package com.bidcollab.repository;

import com.bidcollab.entity.KnowledgeChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
  List<KnowledgeChunk> findByKnowledgeBaseId(Long knowledgeBaseId);
  void deleteByKnowledgeDocumentId(Long knowledgeDocumentId);
  void deleteByKnowledgeBaseId(Long knowledgeBaseId);
}
