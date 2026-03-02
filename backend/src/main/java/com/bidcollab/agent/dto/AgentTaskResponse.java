package com.bidcollab.agent.dto;

import com.bidcollab.agent.task.AgentTaskStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentTaskResponse {
  private String id;
  private Long documentId;
  private Long sectionId;
  private String runMode;
  private AgentTaskStatus status;
  private String errorMessage;
  private String response;
  private Instant createdAt;
  private Instant finishedAt;
}
