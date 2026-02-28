package com.bidcollab.repository;

import com.bidcollab.entity.KnowledgeGraphEdgeIndex;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeGraphEdgeIndexRepository extends JpaRepository<KnowledgeGraphEdgeIndex, Long> {

  @Modifying
  @Query("delete from KnowledgeGraphEdgeIndex e where e.knowledgeDocument.id = :knowledgeDocumentId")
  void deleteByKnowledgeDocumentId(@Param("knowledgeDocumentId") Long knowledgeDocumentId);

  @Modifying
  @Query("delete from KnowledgeGraphEdgeIndex e where e.knowledgeBase.id = :knowledgeBaseId")
  void deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

  @Query("""
      select
        e.knowledgeDocument.id as knowledgeDocumentId,
        e.sourceNodeKey as sourceNodeKey,
        e.sourceNodeName as sourceNodeName,
        e.targetNodeKey as targetNodeKey,
        e.targetNodeName as targetNodeName,
        e.relationType as relationType,
        e.relationName as relationName,
        sum(e.frequency) as frequency
      from KnowledgeGraphEdgeIndex e
      where e.knowledgeBase.id = :knowledgeBaseId
        and e.knowledgeDocument.id in :documentIds
      group by e.knowledgeDocument.id,
        e.sourceNodeKey, e.sourceNodeName,
        e.targetNodeKey, e.targetNodeName,
        e.relationType, e.relationName
      """)
  List<EdgeStatRow> findStatsByKnowledgeBaseIdAndKnowledgeDocumentIdIn(
      @Param("knowledgeBaseId") Long knowledgeBaseId,
      @Param("documentIds") Collection<Long> documentIds);

  interface EdgeStatRow {
    Long getKnowledgeDocumentId();

    String getSourceNodeKey();

    String getSourceNodeName();

    String getTargetNodeKey();

    String getTargetNodeName();

    String getRelationType();

    String getRelationName();

    Integer getFrequency();
  }
}
