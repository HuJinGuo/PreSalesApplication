package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentResponse {
  private Long id;
  private Long projectId;
  private String name;
  private String docType;
  private Integer versionNo;
  private Instant createdAt;
  private Instant updatedAt;
}
