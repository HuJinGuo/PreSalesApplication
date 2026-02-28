package com.bidcollab.repository;

import com.bidcollab.entity.KnowledgeGraphNodeIndex;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeGraphNodeIndexRepository extends JpaRepository<KnowledgeGraphNodeIndex, Long> {

  @Modifying
  @Query("delete from KnowledgeGraphNodeIndex n where n.knowledgeDocument.id = :knowledgeDocumentId")
  void deleteByKnowledgeDocumentId(@Param("knowledgeDocumentId") Long knowledgeDocumentId);

  @Modifying
  @Query("delete from KnowledgeGraphNodeIndex n where n.knowledgeBase.id = :knowledgeBaseId")
  void deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

  @Query("""
      select
        n.knowledgeDocument.id as knowledgeDocumentId,
        n.nodeKey as nodeKey,
        n.nodeName as nodeName,
        n.nodeType as nodeType,
        sum(n.frequency) as frequency
      from KnowledgeGraphNodeIndex n
      where n.knowledgeBase.id = :knowledgeBaseId
        and n.knowledgeDocument.id in :documentIds
      group by n.knowledgeDocument.id, n.nodeKey, n.nodeName, n.nodeType
      """)
  List<NodeStatRow> findStatsByKnowledgeBaseIdAndKnowledgeDocumentIdIn(
      @Param("knowledgeBaseId") Long knowledgeBaseId,
      @Param("documentIds") Collection<Long> documentIds);

  @Query("""
      select
        n.knowledgeDocument.id as knowledgeDocumentId,
        n.knowledgeChunk.id as knowledgeChunkId,
        n.nodeKey as nodeKey,
        n.nodeName as nodeName,
        n.nodeType as nodeType,
        sum(n.frequency) as frequency
      from KnowledgeGraphNodeIndex n
      where n.knowledgeBase.id = :knowledgeBaseId
        and n.nodeKey = :nodeKey
        and n.knowledgeDocument.id in :documentIds
      group by n.knowledgeDocument.id, n.knowledgeChunk.id, n.nodeKey, n.nodeName, n.nodeType
      """)
  List<NodeChunkStatRow> findStatsByKnowledgeBaseIdAndNodeKeyAndKnowledgeDocumentIdIn(
      @Param("knowledgeBaseId") Long knowledgeBaseId,
      @Param("nodeKey") String nodeKey,
      @Param("documentIds") Collection<Long> documentIds);

  interface NodeStatRow {
    Long getKnowledgeDocumentId();

    String getNodeKey();

    String getNodeName();

    String getNodeType();

    Integer getFrequency();
  }

  interface NodeChunkStatRow {
    Long getKnowledgeDocumentId();

    Long getKnowledgeChunkId();

    String getNodeKey();

    String getNodeName();

    String getNodeType();

    Integer getFrequency();
  }
}
