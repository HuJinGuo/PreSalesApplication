package com.bidcollab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiDocumentAutoWriteRequest {
  @NotNull
  private Long documentId;
  private Long knowledgeBaseId;
  private String projectParams;
  private boolean overwriteExisting = true;
}
