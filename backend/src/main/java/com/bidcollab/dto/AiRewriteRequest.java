package com.bidcollab.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiRewriteRequest {
  @NotNull
  private Long sectionId;
  @NotNull
  private Long sourceVersionId;
  @NotBlank
  private String projectParams;
}
