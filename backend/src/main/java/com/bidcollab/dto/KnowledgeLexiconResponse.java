package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeLexiconResponse {
  private Long id;
  private Long knowledgeBaseId;
  private String category;
  private String term;
  private String standardTerm;
  private Boolean enabled;
  private Instant createdAt;
}
