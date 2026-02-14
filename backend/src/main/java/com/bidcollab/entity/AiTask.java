package com.bidcollab.entity;

import com.bidcollab.enums.AiTaskStatus;
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
@Table(name = "ai_task")
public class AiTask extends BaseEntity {
  @Column(name = "section_id")
  private Long sectionId;

  @Column(name = "source_version_id")
  private Long sourceVersionId;

  @Column(name = "result_version_id")
  private Long resultVersionId;

  @Column(columnDefinition = "LONGTEXT")
  private String prompt;

  @Column(columnDefinition = "LONGTEXT")
  private String response;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16)")
  private AiTaskStatus status;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "created_by")
  private Long createdBy;
}
