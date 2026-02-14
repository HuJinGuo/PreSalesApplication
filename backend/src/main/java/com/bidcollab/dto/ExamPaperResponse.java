package com.bidcollab.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExamPaperResponse {
  private Long id;
  private Long knowledgeBaseId;
  private String title;
  private String instructions;
  private BigDecimal totalScore;
  private Instant createdAt;
  private Boolean published;
  private String shareToken;
  private List<ExamQuestionResponse> questions;
}
