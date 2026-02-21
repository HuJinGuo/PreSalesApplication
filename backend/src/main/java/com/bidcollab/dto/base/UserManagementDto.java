package com.bidcollab.dto.base;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserManagementDto {
  private Long id;
  private String username;
  private String realName;
  private String status;
  private Long departmentId;
  private String departmentName;
  private List<Long> roleIds;
  private List<Long> menuIds;

  @Builder.Default
  private List<String> roleCodes = new ArrayList<>();
}
