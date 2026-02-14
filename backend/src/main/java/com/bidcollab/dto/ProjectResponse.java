package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectResponse {
  private Long id;
  private String code;
  private String name;
  private String customerName;
  private String industry;
  private String scale;
  private String status;
  private Instant createdAt;
  private Instant updatedAt;
}
