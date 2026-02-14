package com.bidcollab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "exam_question")
public class ExamQuestion extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "paper_id", nullable = false)
  private ExamPaper paper;

  @Column(name = "question_type", nullable = false, length = 32)
  private String questionType;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String stem;

  @Column(name = "options_json", columnDefinition = "TEXT")
  private String optionsJson;

  @Column(name = "reference_answer", columnDefinition = "TEXT")
  private String referenceAnswer;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal score;

  @Column(name = "sort_index", nullable = false)
  private Integer sortIndex;
}
