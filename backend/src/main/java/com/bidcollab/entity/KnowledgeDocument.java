package com.bidcollab.entity;

import com.bidcollab.enums.KnowledgeVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
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
@Table(name = "knowledge_document")
public class KnowledgeDocument extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_base_id", nullable = false)
  private KnowledgeBase knowledgeBase;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(name = "source_type", nullable = false, length = 32)
  private String sourceType;

  @Column(name = "file_name", length = 255)
  private String fileName;

  @Column(name = "file_type", length = 32)
  private String fileType;

  @Column(name = "storage_path", length = 1024)
  private String storagePath;

  @Column(columnDefinition = "LONGTEXT")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16)")
  private KnowledgeVisibility visibility;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "index_status", nullable = false, length = 16)
  private String indexStatus;

  @Column(name = "index_message", length = 1000)
  private String indexMessage;

  @Column(name = "index_progress", nullable = false)
  private Integer indexProgress;

  @Column(name = "indexed_at")
  private Instant indexedAt;

  @Column(name = "index_task_id", length = 64)
  private String indexTaskId;
}
