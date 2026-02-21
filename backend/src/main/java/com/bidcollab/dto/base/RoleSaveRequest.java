package com.bidcollab.dto.base;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RoleSaveRequest {
  @NotBlank
  private String code;

  @NotBlank
  private String name;

  private List<Long> menuIds = new ArrayList<>();
}
