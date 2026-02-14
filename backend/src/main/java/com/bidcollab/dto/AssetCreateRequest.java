package com.bidcollab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssetCreateRequest {
  @NotNull
  private Long versionId;
  private String industryTag;
  private String scopeTag;
  private Boolean isWinning;
  private String keywords;
}
