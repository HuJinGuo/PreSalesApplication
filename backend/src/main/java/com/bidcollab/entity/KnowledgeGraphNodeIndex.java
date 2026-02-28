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
@Table(name = "knowledge_graph_node")
public class KnowledgeGraphNodeIndex extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_base_id", nullable = false)
  private KnowledgeBase knowledgeBase;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_document_id", nullable = false)
  private KnowledgeDocument knowledgeDocument;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_chunk_id", nullable = false)
  private KnowledgeChunk knowledgeChunk;

  @Column(name = "node_key", nullable = false, length = 255)
  private String nodeKey;

  @Column(name = "node_name", nullable = false, length = 255)
  private String nodeName;

  @Column(name = "node_type", nullable = false, length = 64)
  private String nodeType;

  @Column(name = "frequency", nullable = false)
  private Integer frequency;

  @Column(name = "source", nullable = false, length = 32)
  private String source;
}
