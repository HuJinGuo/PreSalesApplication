package com.bidcollab.dto.graph;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeGraphDetailChunk {
  private Long chunkId;
  private Long documentId;
  private String documentTitle;
  private Integer chunkIndex;
  private String snippet;
  private Integer hitCount;
}

