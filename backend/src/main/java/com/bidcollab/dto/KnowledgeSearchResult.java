package com.bidcollab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeSearchResult {
  private Long chunkId;
  private Long documentId;
  private String content;
  private double score;
}
