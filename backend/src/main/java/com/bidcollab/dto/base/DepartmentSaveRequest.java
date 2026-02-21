package com.bidcollab.dto.base;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DepartmentSaveRequest {
  @NotBlank
  private String code;

  @NotBlank
  private String name;

  private String managerName;

  @NotBlank
  private String status;
}
