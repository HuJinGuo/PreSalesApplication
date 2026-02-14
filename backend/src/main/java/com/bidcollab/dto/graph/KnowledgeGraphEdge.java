package com.bidcollab.dto.graph;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeGraphEdge {
  private String source;
  private String target;
  private String label;
  private Integer value;
}
