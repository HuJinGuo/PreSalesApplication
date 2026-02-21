package com.bidcollab.dto.base;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DepartmentDto {
  private Long id;
  private String code;
  private String name;
  private String managerName;
  private String status;
}
