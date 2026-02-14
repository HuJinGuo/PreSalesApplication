package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SectionCreateRequest {
  private Long parentId;
  @NotBlank
  private String title;
  @NotNull
  private Integer level;
  @NotNull
  private Integer sortIndex;
}
