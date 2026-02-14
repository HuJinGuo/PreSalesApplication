package com.bidcollab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgeDocumentGrantRequest {
  @NotNull
  private Long userId;
}
