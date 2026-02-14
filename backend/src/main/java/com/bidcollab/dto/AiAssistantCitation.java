package com.bidcollab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiAssistantCitation {
  private Long knowledgeBaseId;
  private String knowledgeBaseName;
  private Long documentId;
  private String documentTitle;
  private Long chunkId;
  private Double score;
  private String snippet;
}
