package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DomainCategoryRelationResponse {
  private Long id;
  private Long sourceCategoryId;
  private String sourceCategory;
  private String sourceCategoryName;
  private Long targetCategoryId;
  private String targetCategory;
  private String targetCategoryName;
  private String relationLabel;
  private Boolean enabled;
  private Instant createdAt;
}
