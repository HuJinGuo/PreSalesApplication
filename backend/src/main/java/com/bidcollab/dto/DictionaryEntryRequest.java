package com.bidcollab.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class DictionaryEntryRequest {
  private Long categoryId;

  private String category;
  @NotBlank
  private String term;

  private String standardTerm;

  private Boolean enabled;
}
