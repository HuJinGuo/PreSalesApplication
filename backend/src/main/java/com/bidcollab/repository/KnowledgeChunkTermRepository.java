package com.bidcollab.repository;

import com.bidcollab.entity.KnowledgeChunkTerm;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface KnowledgeChunkTermRepository extends JpaRepository<KnowledgeChunkTerm, Long> {
  interface TermStatRow {
    Long getKnowledgeDocumentId();
    Long getKnowledgeChunkId();
    String getTermType();
    String getTermKey();
    String getTermName();
    Integer getFrequency();
  }

  @Modifying
  @Transactional
  @Query("delete from KnowledgeChunkTerm t where t.knowledgeDocument.id = :knowledgeDocumentId")
  void deleteByKnowledgeDocumentId(@Param("knowledgeDocumentId") Long knowledgeDocumentId);

  @Modifying
  @Transactional
  @Query("delete from KnowledgeChunkTerm t where t.knowledgeBase.id = :knowledgeBaseId")
  void deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

  @Query("""
      select t.knowledgeDocument.id as knowledgeDocumentId,
             t.knowledgeChunk.id as knowledgeChunkId,
             t.termType as termType,
             t.termKey as termKey,
             t.termName as termName,
             t.frequency as frequency
      from KnowledgeChunkTerm t
      where t.knowledgeBase.id = :knowledgeBaseId
        and t.knowledgeDocument.id in :documentIds
      """)
  List<TermStatRow> findStatsByKnowledgeBaseIdAndKnowledgeDocumentIdIn(
      @Param("knowledgeBaseId") Long knowledgeBaseId,
      @Param("documentIds") Collection<Long> documentIds);

  @Query("""
      select t.knowledgeDocument.id as knowledgeDocumentId,
             t.knowledgeChunk.id as knowledgeChunkId,
             t.termType as termType,
             t.termKey as termKey,
             t.termName as termName,
             t.frequency as frequency
      from KnowledgeChunkTerm t
      where t.knowledgeBase.id = :knowledgeBaseId
        and t.termType = :termType
        and t.termKey = :termKey
        and t.knowledgeDocument.id in :documentIds
      """)
  List<TermStatRow> findStatsByKnowledgeBaseIdAndTermTypeAndTermKeyAndKnowledgeDocumentIdIn(
      @Param("knowledgeBaseId") Long knowledgeBaseId,
      @Param("termType") String termType,
      @Param("termKey") String termKey,
      @Param("documentIds") Collection<Long> documentIds);
}
