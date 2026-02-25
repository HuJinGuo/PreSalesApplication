package com.bidcollab.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTokenUsageDailyResponse {
  private LocalDate startDate;
  private LocalDate endDate;
  private long totalPromptTokens;
  private long totalCompletionTokens;
  private long totalTokens;
  private long totalRequests;
  private long successRequests;
  private List<AiTokenUsageDailyItem> daily;
  private List<AiTokenUsageProviderItem> providers;
  private List<AiTokenUsageModelItem> models;
}
