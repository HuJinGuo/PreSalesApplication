package com.bidcollab.repository;

import com.bidcollab.agent.task.AgentStepStatus;
import com.bidcollab.entity.AgentWorkflowStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkflowStepRepository extends JpaRepository<AgentWorkflowStep, Long> {
  List<AgentWorkflowStep> findByTask_IdOrderByIterationNoAscCreatedAtAsc(Long taskId);

  boolean existsByTask_IdAndIdempotencyKeyAndStatus(Long taskId, String idempotencyKey, AgentStepStatus status);

  long countByTask_Id(Long taskId);

  long countByTask_IdAndStatus(Long taskId, AgentStepStatus status);
}
