package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewRequest {
  @NotNull
  private Long versionId;
  @NotBlank
  private String comment;
}
