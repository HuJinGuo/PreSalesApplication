package com.bidcollab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTokenUsageModelItem {
  private String provider;
  private String modelName;
  private long promptTokens;
  private long completionTokens;
  private long totalTokens;
  private long requestCount;
}
