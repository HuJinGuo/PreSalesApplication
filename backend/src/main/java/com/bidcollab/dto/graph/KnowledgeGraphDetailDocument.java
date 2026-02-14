package com.bidcollab.dto.graph;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeGraphDetailDocument {
  private Long documentId;
  private String title;
  private Integer hitCount;
}

