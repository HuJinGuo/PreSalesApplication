package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DictionaryPackRequest {
  @NotBlank
  private String code;

  @NotBlank
  private String name;

  private String scopeType;

  private String status;

  private String description;
}
