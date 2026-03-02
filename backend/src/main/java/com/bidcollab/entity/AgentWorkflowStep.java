package com.bidcollab.entity;

import com.bidcollab.agent.task.AgentStepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "agent_workflow_step")
public class AgentWorkflowStep extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "task_id", nullable = false)
  private AgentWorkflowTask task;

  @Column(name = "iteration_no", nullable = false)
  private Integer iterationNo;

  @Column(name = "step_code", nullable = false, length = 64)
  private String stepCode;

  @Column(name = "step_name", nullable = false, length = 128)
  private String stepName;

  @Column(name = "step_type", nullable = false, length = 32)
  private String stepType;

  @Column(name = "tool_name", length = 64)
  private String toolName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16, columnDefinition = "VARCHAR(16)")
  private AgentStepStatus status;

  @Column(name = "reason", columnDefinition = "TEXT")
  private String reason;

  @Column(name = "args_json", columnDefinition = "LONGTEXT")
  private String argsJson;

  @Column(name = "observation", columnDefinition = "LONGTEXT")
  private String observation;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "idempotency_key", length = 128)
  private String idempotencyKey;

  @Column(name = "retry_count", nullable = false)
  private Integer retryCount;

  @Column(name = "timeout_ms", nullable = false)
  private Long timeoutMs;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;
}
