package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeSearchRequest {
  @NotBlank
  private String query;
  private Integer topK = 5;
  private Integer candidateTopK = 30;
  private Double minScore = 0.0;
  private Boolean rerank = true;
  private List<Long> documentIds;
}
