package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DomainCategoryRelationRequest {
  @NotNull
  private Long sourceCategoryId;

  private String sourceCategory;

  @NotNull
  private Long targetCategoryId;

  private String targetCategory;

  @NotBlank
  private String relationLabel;

  private Boolean enabled;
}
