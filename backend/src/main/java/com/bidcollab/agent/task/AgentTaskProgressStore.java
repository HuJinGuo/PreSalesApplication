package com.bidcollab.agent.task;

import com.bidcollab.agent.dto.AgentTaskResponse;
import com.bidcollab.agent.dto.AgentTaskListItemResponse;
import com.bidcollab.entity.AgentWorkflowStep;
import com.bidcollab.entity.AgentWorkflowTask;
import com.bidcollab.repository.AgentWorkflowStepRepository;
import com.bidcollab.repository.AgentWorkflowTaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 任务进度存储（数据库版）。
 */
@Component
@Slf4j
public class AgentTaskProgressStore {
  private final AgentWorkflowTaskRepository taskRepository;
  private final AgentWorkflowStepRepository stepRepository;
  private final ObjectMapper objectMapper;

  public AgentTaskProgressStore(AgentWorkflowTaskRepository taskRepository,
      AgentWorkflowStepRepository stepRepository,
      ObjectMapper objectMapper) {
    this.taskRepository = taskRepository;
    this.stepRepository = stepRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public AgentTaskResponse createTask(Long documentId, Long sectionId, Long knowledgeBaseId, String runMode,
      String requirement, String projectParams, Long createdBy, int maxIterations) {
    AgentWorkflowTask task = new AgentWorkflowTask();
    task.setDocumentId(documentId);
    task.setSectionId(sectionId);
    task.setKnowledgeBaseId(knowledgeBaseId);
    task.setRunMode(runMode);
    task.setRequirement(requirement);
    task.setProjectParams(projectParams);
    task.setMaxIterations(maxIterations);
    task.setCurrentIteration(0);
    task.setStatus(AgentTaskStatus.RUNNING);
    task.setStartedAt(Instant.now());
    task.setCreatedBy(createdBy);
    taskRepository.save(task);
    return toResponse(task);
  }

  @Transactional
  public Long startStep(Long taskId, int iterationNo, String stepCode, String stepName, String stepType,
      String toolName, String reason, Map<String, Object> args, String idempotencyKey, long timeoutMs) {
    AgentWorkflowTask task = taskRepository.findById(taskId).orElseThrow(EntityNotFoundException::new);
    task.setCurrentIteration(iterationNo);
    taskRepository.save(task);

    AgentWorkflowStep step = new AgentWorkflowStep();
    step.setTask(task);
    step.setIterationNo(iterationNo);
    step.setStepCode(stepCode);
    step.setStepName(stepName);
    step.setStepType(stepType);
    step.setToolName(toolName);
    step.setStatus(AgentStepStatus.RUNNING);
    step.setReason(reason);
    step.setArgsJson(toJson(args));
    step.setIdempotencyKey(idempotencyKey);
    step.setRetryCount(0);
    step.setTimeoutMs(timeoutMs);
    step.setStartedAt(Instant.now());
    stepRepository.save(step);
    return step.getId();
  }

  @Transactional
  public void finishStep(Long stepId, boolean success, String observation, String error, int retryCount) {
    AgentWorkflowStep step = stepRepository.findById(stepId).orElseThrow(EntityNotFoundException::new);
    step.setStatus(success ? AgentStepStatus.SUCCESS : AgentStepStatus.FAILED);
    step.setObservation(observation);
    step.setErrorMessage(error);
    step.setRetryCount(Math.max(0, retryCount));
    step.setFinishedAt(Instant.now());
    stepRepository.save(step);
  }

  @Transactional(readOnly = true)
  public boolean hasSuccessfulIdempotencyStep(Long taskId, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return false;
    }
    return stepRepository.existsByTask_IdAndIdempotencyKeyAndStatus(taskId, idempotencyKey, AgentStepStatus.SUCCESS);
  }

  @Transactional
  public void finishTaskSuccess(Long taskId, String summary) {
    AgentWorkflowTask task = taskRepository.findById(taskId).orElseThrow(EntityNotFoundException::new);
    task.setStatus(AgentTaskStatus.SUCCESS);
    task.setFinalSummary(summary);
    task.setErrorMessage(null);
    task.setFinishedAt(Instant.now());
    taskRepository.save(task);
  }

  @Transactional
  public void finishTaskFailed(Long taskId, String error) {
    AgentWorkflowTask task = taskRepository.findById(taskId).orElseThrow(EntityNotFoundException::new);
    task.setStatus(AgentTaskStatus.FAILED);
    task.setErrorMessage(error);
    task.setFinishedAt(Instant.now());
    taskRepository.save(task);
  }

  @Transactional(readOnly = true)
  public AgentTaskResponse getTask(Long taskId) {
    AgentWorkflowTask task = taskRepository.findById(taskId).orElse(null);
    if (task == null) {
      return null;
    }
    List<AgentWorkflowStep> steps = stepRepository.findByTask_IdOrderByIterationNoAscCreatedAtAsc(taskId);
    return toResponse(task, steps);
  }

  @Transactional(readOnly = true)
  public List<AgentTaskListItemResponse> listTasksByDocument(Long documentId, int limit) {
    int pageSize = Math.min(Math.max(limit, 1), 50);
    List<AgentWorkflowTask> tasks = taskRepository.findByDocumentIdOrderByCreatedAtDesc(
        documentId, PageRequest.of(0, pageSize));
    List<AgentTaskListItemResponse> rows = new ArrayList<>(tasks.size());
    for (AgentWorkflowTask task : tasks) {
      long total = stepRepository.countByTask_Id(task.getId());
      long success = stepRepository.countByTask_IdAndStatus(task.getId(), AgentStepStatus.SUCCESS);
      long failed = stepRepository.countByTask_IdAndStatus(task.getId(), AgentStepStatus.FAILED);
      rows.add(AgentTaskListItemResponse.builder()
          .id(String.valueOf(task.getId()))
          .documentId(task.getDocumentId())
          .sectionId(task.getSectionId())
          .runMode(task.getRunMode())
          .status(task.getStatus())
          .errorMessage(task.getErrorMessage())
          .totalSteps((int) total)
          .successSteps((int) success)
          .failedSteps((int) failed)
          .createdAt(task.getCreatedAt())
          .finishedAt(task.getFinishedAt())
          .build());
    }
    return rows;
  }

  private AgentTaskResponse toResponse(AgentWorkflowTask task) {
    return toResponse(task, List.of());
  }

  private AgentTaskResponse toResponse(AgentWorkflowTask task, List<AgentWorkflowStep> steps) {
    int total = steps.size();
    int success = 0;
    int failed = 0;
    List<Map<String, Object>> rows = new ArrayList<>();
    for (AgentWorkflowStep step : steps) {
      if (step.getStatus() == AgentStepStatus.SUCCESS) {
        success++;
      } else if (step.getStatus() == AgentStepStatus.FAILED) {
        failed++;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", step.getStepCode());
      row.put("name", step.getStepName());
      row.put("type", step.getStepType());
      row.put("tool", step.getToolName());
      row.put("status", step.getStatus().name());
      row.put("reason", step.getReason());
      row.put("error", step.getErrorMessage());
      row.put("observation", step.getObservation());
      row.put("retryCount", step.getRetryCount());
      row.put("timeoutMs", step.getTimeoutMs());
      row.put("idempotencyKey", step.getIdempotencyKey());
      row.put("startedAt", step.getStartedAt());
      row.put("finishedAt", step.getFinishedAt());
      rows.add(row);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("mode", task.getRunMode());
    payload.put("maxIterations", task.getMaxIterations());
    payload.put("currentIteration", task.getCurrentIteration());
    payload.put("total", total);
    payload.put("success", success);
    payload.put("failed", failed);
    payload.put("finalSummary", task.getFinalSummary());
    payload.put("steps", rows);
      return AgentTaskResponse.builder()
          .id(String.valueOf(task.getId()))
          .documentId(task.getDocumentId())
          .sectionId(task.getSectionId())
          .runMode(task.getRunMode())
        .status(task.getStatus())
        .errorMessage(task.getErrorMessage())
        .response(toJson(payload))
        .createdAt(task.getCreatedAt())
        .finishedAt(task.getFinishedAt())
        .build();
  }

  private String toJson(Object obj) {
    if (obj == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      log.warn("[Agent][Progress] json serialize failed", e);
      return "{}";
    }
  }
}
