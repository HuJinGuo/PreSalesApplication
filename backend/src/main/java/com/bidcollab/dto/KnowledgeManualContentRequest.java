package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeManualContentRequest {
  @NotBlank
  private String title;
  @NotBlank
  private String content;
}
