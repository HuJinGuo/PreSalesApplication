package com.bidcollab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "knowledge_graph_edge")
public class KnowledgeGraphEdgeIndex extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_base_id", nullable = false)
  private KnowledgeBase knowledgeBase;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_document_id", nullable = false)
  private KnowledgeDocument knowledgeDocument;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_chunk_id", nullable = false)
  private KnowledgeChunk knowledgeChunk;

  @Column(name = "source_node_key", nullable = false, length = 255)
  private String sourceNodeKey;

  @Column(name = "source_node_name", nullable = false, length = 255)
  private String sourceNodeName;

  @Column(name = "target_node_key", nullable = false, length = 255)
  private String targetNodeKey;

  @Column(name = "target_node_name", nullable = false, length = 255)
  private String targetNodeName;

  @Column(name = "relation_type", nullable = false, length = 64)
  private String relationType;

  @Column(name = "relation_name", nullable = false, length = 128)
  private String relationName;

  @Column(name = "frequency", nullable = false)
  private Integer frequency;

  @Column(name = "source", nullable = false, length = 32)
  private String source;
}
