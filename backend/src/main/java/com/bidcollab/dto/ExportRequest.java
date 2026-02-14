package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExportRequest {
  @NotBlank
  private String format;
}
