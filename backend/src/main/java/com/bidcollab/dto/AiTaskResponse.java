package com.bidcollab.dto;

import com.bidcollab.enums.AiTaskStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTaskResponse {
  private Long id;
  private Long sectionId;
  private Long sourceVersionId;
  private Long resultVersionId;
  private AiTaskStatus status;
  private String errorMessage;
  private Instant createdAt;
}
