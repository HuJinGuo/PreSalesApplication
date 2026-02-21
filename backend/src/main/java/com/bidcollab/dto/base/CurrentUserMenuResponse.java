package com.bidcollab.dto.base;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CurrentUserMenuResponse {
  private String username;
  private String realName;

  @Builder.Default
  private List<MenuDto> menus = new ArrayList<>();
}
