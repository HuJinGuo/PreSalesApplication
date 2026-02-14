package com.bidcollab.dto;

import com.bidcollab.enums.KnowledgeVisibility;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgeDocumentVisibilityUpdateRequest {
  @NotNull
  private KnowledgeVisibility visibility;
}
