package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeBaseResponse {
  private Long id;
  private String name;
  private String description;
  private Instant createdAt;
}
