package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeLexiconUpsertRequest {
  @NotBlank
  private String category;
  @NotBlank
  private String term;
  private Boolean enabled;
}

