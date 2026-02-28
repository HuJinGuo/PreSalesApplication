package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DomainCategoryRequest {
  @NotBlank
  private String code;

  @NotBlank
  private String name;

  private String description;

  private String status;

  private Integer sortOrder;
}
