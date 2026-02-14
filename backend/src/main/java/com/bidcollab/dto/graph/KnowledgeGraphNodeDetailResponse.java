package com.bidcollab.dto.graph;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeGraphNodeDetailResponse {
  private String nodeId;
  private String nodeType;
  private String name;
  private String summary;
  private List<KnowledgeGraphDetailDocument> relatedDocuments;
  private List<String> relatedKeywords;
  private List<KnowledgeGraphDetailChunk> relatedChunks;
}

