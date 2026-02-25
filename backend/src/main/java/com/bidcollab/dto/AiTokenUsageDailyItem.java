package com.bidcollab.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTokenUsageDailyItem {
  private LocalDate usageDate;
  private long promptTokens;
  private long completionTokens;
  private long totalTokens;
  private long requestCount;
  private long successCount;
}
