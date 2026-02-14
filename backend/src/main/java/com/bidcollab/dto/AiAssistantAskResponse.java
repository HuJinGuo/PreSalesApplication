package com.bidcollab.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiAssistantAskResponse {
  private String answer;
  private Integer matchedChunkCount;
  private List<AiAssistantCitation> citations;
}
