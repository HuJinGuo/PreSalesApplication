package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiAssistantAskRequest {
  @NotBlank
  private String query;
  private Long knowledgeBaseId;
  private Integer topK = 8;
  private Double minScore = 0.15;
  private Boolean rerank = true;
}
