package com.bidcollab.entity;

import com.bidcollab.agent.task.AgentTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "agent_workflow_task")
public class AgentWorkflowTask extends BaseEntity {
  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "section_id")
  private Long sectionId;

  @Column(name = "knowledge_base_id")
  private Long knowledgeBaseId;

  @Column(name = "run_mode", nullable = false, length = 32)
  private String runMode;

  @Column(name = "requirement", columnDefinition = "TEXT")
  private String requirement;

  @Column(name = "project_params", columnDefinition = "LONGTEXT")
  private String projectParams;

  @Column(name = "max_iterations", nullable = false)
  private Integer maxIterations;

  @Column(name = "current_iteration", nullable = false)
  private Integer currentIteration;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16, columnDefinition = "VARCHAR(16)")
  private AgentTaskStatus status;

  @Column(name = "final_summary", columnDefinition = "TEXT")
  private String finalSummary;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "created_by")
  private Long createdBy;
}
