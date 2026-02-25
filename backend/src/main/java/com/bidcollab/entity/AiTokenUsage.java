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
@Table(name = "ai_token_usage")
public class AiTokenUsage extends BaseEntity {
  @Column(name = "usage_date", nullable = false)
  private LocalDate usageDate;

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

  @Column(name = "created_by")
  private Long createdBy;
}
