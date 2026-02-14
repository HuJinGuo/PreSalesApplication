package com.bidcollab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReuseRequest {
  @NotNull
  private Long assetId;
  @NotNull
  private Long targetParentId;
  @NotNull
  private Integer targetSortIndex;
  @NotNull
  private Integer targetLevel;
  @NotNull
  private Long documentId;
}
