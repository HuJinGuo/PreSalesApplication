package com.bidcollab.repository;

import com.bidcollab.entity.KnowledgeChunk;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
  List<KnowledgeChunk> findByKnowledgeBaseId(Long knowledgeBaseId);
  List<KnowledgeChunk> findByKnowledgeBaseIdAndKnowledgeDocumentIdIn(Long knowledgeBaseId, Collection<Long> documentIds);
  List<KnowledgeChunk> findByKnowledgeBaseIdAndKnowledgeDocumentIdOrderByChunkIndexAsc(Long knowledgeBaseId, Long documentId);

  @Modifying
  @Transactional
  @Query("delete from KnowledgeChunk c where c.knowledgeDocument.id = :knowledgeDocumentId")
  void deleteByKnowledgeDocumentId(@Param("knowledgeDocumentId") Long knowledgeDocumentId);

  @Modifying
  @Transactional
  @Query("delete from KnowledgeChunk c where c.knowledgeBase.id = :knowledgeBaseId")
  void deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
}
