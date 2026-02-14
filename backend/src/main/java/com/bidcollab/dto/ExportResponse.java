package com.bidcollab.dto;

import com.bidcollab.enums.ExportStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExportResponse {
  private Long id;
  private Long documentId;
  private String format;
  private ExportStatus status;
  private String filePath;
  private Instant createdAt;
}
