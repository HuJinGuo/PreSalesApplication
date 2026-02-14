package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectCreateRequest {
  @NotBlank
  private String code;
  @NotBlank
  private String name;
  private String customerName;
  private String industry;
  private String scale;
}
