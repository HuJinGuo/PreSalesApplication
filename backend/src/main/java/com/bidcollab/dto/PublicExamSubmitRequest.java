package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublicExamSubmitRequest {
  @NotBlank
  private String submitterName;
  @NotBlank
  private String answersJson;
}
