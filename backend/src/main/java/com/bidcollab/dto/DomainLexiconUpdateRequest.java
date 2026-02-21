package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DomainLexiconUpdateRequest {
  @NotNull
  private Long knowledgeBaseId;
  @NotBlank
  private String category;
  @NotBlank
  private String term;
  private String standardTerm;
  private Boolean enabled;
}
