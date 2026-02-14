package com.bidcollab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SectionMoveRequest {
  @NotNull
  private Long targetParentId;
  @NotNull
  private Integer targetSortIndex;
}
