package com.bidcollab.dto;

import com.bidcollab.entity.AiTask;
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
  private String response;
  private Instant createdAt;

  /**
   * 从实体对象构建响应 DTO（消除各 Service 中重复的 toResponse 私有方法）。
   */
  public static AiTaskResponse from(AiTask task) {
    return AiTaskResponse.builder()
        .id(task.getId())
        .sectionId(task.getSectionId())
        .sourceVersionId(task.getSourceVersionId())
        .resultVersionId(task.getResultVersionId())
        .status(task.getStatus())
        .errorMessage(task.getErrorMessage())
        .response(task.getResponse())
        .createdAt(task.getCreatedAt())
        .build();
  }
}
