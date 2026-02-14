package com.bidcollab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "exam_paper")
public class ExamPaper extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "knowledge_base_id", nullable = false)
  private KnowledgeBase knowledgeBase;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String instructions;

  @Column(name = "total_score", nullable = false, precision = 10, scale = 2)
  private BigDecimal totalScore;

  @Column(name = "generated_by")
  private Long generatedBy;

  @Column(name = "share_token", length = 64, unique = true)
  private String shareToken;

  @Column(name = "is_published", nullable = false)
  private Boolean published;

  @Column(name = "published_at")
  private Instant publishedAt;
}
