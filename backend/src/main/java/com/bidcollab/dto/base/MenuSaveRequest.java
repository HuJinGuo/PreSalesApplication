package com.bidcollab.dto.base;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MenuSaveRequest {
  private Long parentId;

  @NotBlank
  private String title;

  private String path;

  private String icon;

  @NotNull
  private Integer sortIndex;

  @NotNull
  private Boolean visible;
}
