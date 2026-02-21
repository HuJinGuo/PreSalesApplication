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
@Table(name = "knowledge_chunk_term")
public class KnowledgeChunkTerm extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_base_id", nullable = false)
  private KnowledgeBase knowledgeBase;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_document_id", nullable = false)
  private KnowledgeDocument knowledgeDocument;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_chunk_id", nullable = false)
  private KnowledgeChunk knowledgeChunk;

  @Column(name = "term_type", nullable = false, length = 32)
  private String termType; // KEYWORD / DOMAIN_ENTITY

  @Column(name = "term_key", nullable = false, length = 255)
  private String termKey; // keyword 或 "TYPE|term"

  @Column(name = "term_name", nullable = false, length = 255)
  private String termName; // 展示名称

  @Column(name = "frequency", nullable = false)
  private Integer frequency;
}

