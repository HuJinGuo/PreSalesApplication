package com.bidcollab.entity;

import com.bidcollab.enums.ExportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "document_export")
public class DocumentExport extends BaseEntity {
  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(nullable = false, length = 16)
  private String format;

  @Column(name = "version_no", nullable = false, length = 64)
  private String versionNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16)")
  private ExportStatus status;

  @Column(name = "file_path", length = 512)
  private String filePath;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "created_by")
  private Long createdBy;
}
