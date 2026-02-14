package com.bidcollab.dto.graph;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeGraphResponse {
  private List<KnowledgeGraphNode> nodes;
  private List<KnowledgeGraphEdge> edges;
}
