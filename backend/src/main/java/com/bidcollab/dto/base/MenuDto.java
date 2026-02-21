package com.bidcollab.dto.base;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MenuDto {
  private Long id;
  private Long parentId;
  private String title;
  private String path;
  private String icon;
  private Integer sortIndex;
  private Boolean visible;

  @Builder.Default
  private List<MenuDto> children = new ArrayList<>();
}
