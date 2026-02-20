package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentVersionResponse {
  private Long id;
  private Long documentId;
  private String summary;
  private String snapshotJson;
  private Long createdBy;
  private Instant createdAt;
}
