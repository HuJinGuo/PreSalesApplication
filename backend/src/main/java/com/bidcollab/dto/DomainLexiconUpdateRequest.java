package com.bidcollab.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DomainLexiconUpdateRequest {
  @NotNull
  private Long knowledgeBaseId;
  @NotNull
  private Long categoryId;

  private String category;
  @NotBlank
  private String term;
  private String standardTerm;
  private Boolean enabled;
}
