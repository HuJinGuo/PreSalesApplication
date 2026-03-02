package com.bidcollab.agent.dto;

import com.bidcollab.agent.task.AgentTaskStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/**
 * 文档维度任务列表项（用于进度弹窗先展示最近任务）。
 */
@Data
@Builder
public class AgentTaskListItemResponse {
  private String id;
  private Long documentId;
  private Long sectionId;
  private String runMode;
  private AgentTaskStatus status;
  private String errorMessage;
  private Integer totalSteps;
  private Integer successSteps;
  private Integer failedSteps;
  private Instant createdAt;
  private Instant finishedAt;
}

