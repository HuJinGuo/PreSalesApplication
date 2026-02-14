package com.bidcollab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssetResponse {
  private Long id;
  private Long sectionId;
  private Long versionId;
  private String industryTag;
  private String scopeTag;
  private Boolean isWinning;
  private String keywords;
}
