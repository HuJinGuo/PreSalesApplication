package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DictionaryEntryRequest {
  @NotBlank
  private String category;

  @NotBlank
  private String term;

  private String standardTerm;

  private Boolean enabled;
}
