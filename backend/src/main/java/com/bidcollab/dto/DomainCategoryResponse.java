package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DomainCategoryResponse {
  private Long id;
  private String code;
  private String name;
  private String description;
  private String status;
  private Integer sortOrder;
  private Instant createdAt;
}
