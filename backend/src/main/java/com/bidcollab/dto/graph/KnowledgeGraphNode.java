package com.bidcollab.dto.graph;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeGraphNode {
  private String id;
  private String name;
  private String category;
  private Integer value;
}
