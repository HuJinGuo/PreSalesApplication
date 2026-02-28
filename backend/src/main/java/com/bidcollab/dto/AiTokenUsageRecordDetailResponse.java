package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTokenUsageRecordDetailResponse {
  private Long id;
  private String traceId;
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
  private Integer httpStatus;
  private String errorCode;
  private String errorMessage;
  private String vendorRequestId;
  private Integer retryCount;
  private Long knowledgeBaseId;
  private Long knowledgeDocumentId;
  private Long sectionId;
  private Long aiTaskId;
  private String requestPayload;
  private String responsePayload;
  private Long createdBy;
}
