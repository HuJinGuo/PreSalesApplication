package com.bidcollab.entity;

import com.bidcollab.enums.ExamSubmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "exam_submission")
public class ExamSubmission extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "paper_id", nullable = false)
  private ExamPaper paper;

  @Column(name = "submitter_id")
  private Long submitterId;

  @Column(name = "submitter_name", length = 128)
  private String submitterName;

  @Column(name = "answers_json", columnDefinition = "LONGTEXT", nullable = false)
  private String answersJson;

  @Column(precision = 10, scale = 2)
  private BigDecimal score;

  @Column(name = "ai_feedback", columnDefinition = "LONGTEXT")
  private String aiFeedback;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32)")
  private ExamSubmissionStatus status;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

  @Column(name = "graded_at")
  private Instant gradedAt;
}
