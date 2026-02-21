package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DictionaryBatchUpsertRequest {
  @NotBlank
  private String content;

  private String format;
}
