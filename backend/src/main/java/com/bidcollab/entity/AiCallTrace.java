package com.bidcollab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_call_trace")
public class AiCallTrace extends BaseEntity {
  @Column(name = "usage_date", nullable = false)
  private LocalDate usageDate;

  @Column(name = "trace_id", length = 64, nullable = false)
  private String traceId;

  @Column(name = "request_type", length = 32, nullable = false)
  private String requestType;

  @Column(name = "provider", length = 32, nullable = false)
  private String provider;

  @Column(name = "model_name", length = 128, nullable = false)
  private String modelName;

  @Column(name = "scene", length = 64)
  private String scene;

  @Column(name = "prompt_tokens", nullable = false)
  private Integer promptTokens;

  @Column(name = "completion_tokens", nullable = false)
  private Integer completionTokens;

  @Column(name = "total_tokens", nullable = false)
  private Integer totalTokens;

  @Column(name = "latency_ms", nullable = false)
  private Long latencyMs;

  @Column(name = "is_estimated", nullable = false)
  private Boolean estimated;

  @Column(name = "is_success", nullable = false)
  private Boolean success;

  @Column(name = "http_status")
  private Integer httpStatus;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "vendor_request_id", length = 128)
  private String vendorRequestId;

  @Column(name = "knowledge_base_id")
  private Long knowledgeBaseId;

  @Column(name = "knowledge_document_id")
  private Long knowledgeDocumentId;

  @Column(name = "section_id")
  private Long sectionId;

  @Column(name = "ai_task_id")
  private Long aiTaskId;

  @Column(name = "retry_count", nullable = false)
  private Integer retryCount;

  @Column(name = "request_payload", columnDefinition = "MEDIUMTEXT")
  private String requestPayload;

  @Column(name = "response_payload", columnDefinition = "MEDIUMTEXT")
  private String responsePayload;

  @Column(name = "created_by")
  private Long createdBy;
}
