package com.bidcollab.dto.base;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoleDto {
  private Long id;
  private String code;
  private String name;

  @Builder.Default
  private List<Long> menuIds = new ArrayList<>();
}
