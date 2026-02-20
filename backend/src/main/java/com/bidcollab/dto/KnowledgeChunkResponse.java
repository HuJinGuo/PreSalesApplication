package com.bidcollab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeChunkResponse {
  private Long id;
  private Long knowledgeBaseId;
  private Long knowledgeDocumentId;
  private Integer chunkIndex;
  private String content;
  private Integer embeddingDim;
}
