package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTokenUsageRecordItem {
  private Long id;
  private Instant createdAt;
  private String requestType;
  private String provider;
  private String modelName;
  private String scene;
  private int promptTokens;
  private int completionTokens;
  private int totalTokens;
  private long latencyMs;
  private boolean estimated;
  private boolean success;
  private Long createdBy;
}
