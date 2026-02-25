package com.bidcollab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTokenUsageProviderItem {
  private String provider;
  private long promptTokens;
  private long completionTokens;
  private long totalTokens;
  private long requestCount;
}
