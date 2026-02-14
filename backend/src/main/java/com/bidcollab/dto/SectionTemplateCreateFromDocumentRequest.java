package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SectionTemplateCreateFromDocumentRequest {
  @NotNull
  private Long documentId;
  @NotBlank
  private String name;
  private String description;
}

