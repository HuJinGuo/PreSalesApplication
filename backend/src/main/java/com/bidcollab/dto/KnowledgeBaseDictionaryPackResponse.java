package com.bidcollab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeBaseDictionaryPackResponse {
  private Long id;
  private Long knowledgeBaseId;
  private Long packId;
  private String packName;
  private String packCode;
  private Integer priority;
  private Boolean enabled;
}
