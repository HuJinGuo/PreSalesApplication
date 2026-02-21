package com.bidcollab.dto.base;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class UserSaveRequest {
  @NotBlank
  private String username;

  private String password;

  private String realName;

  @NotBlank
  private String status;

  private Long departmentId;

  private List<Long> roleIds = new ArrayList<>();

  private List<Long> menuIds = new ArrayList<>();
}
