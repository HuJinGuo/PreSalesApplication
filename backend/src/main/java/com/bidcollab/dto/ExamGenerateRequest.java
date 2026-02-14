package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExamGenerateRequest {
  @NotNull
  private Long knowledgeBaseId;
  @NotBlank
  private String title;
  private String instructions;
  private Integer singleChoiceCount = 5;
  private Integer judgeCount = 5;
  private Integer blankCount = 3;
  private Integer essayCount = 2;
}
