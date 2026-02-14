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
@Table(name = "knowledge_chunk")
public class KnowledgeChunk extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_base_id", nullable = false)
  private KnowledgeBase knowledgeBase;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_document_id")
  private KnowledgeDocument knowledgeDocument;

  @Column(name = "chunk_index", nullable = false)
  private Integer chunkIndex;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(name = "embedding_json", columnDefinition = "LONGTEXT", nullable = false)
  private String embeddingJson;

  @Column(name = "embedding_dim", nullable = false)
  private Integer embeddingDim;
}
