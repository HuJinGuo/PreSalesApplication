package com.bidcollab.agent.task;

import com.bidcollab.agent.dto.AgentTaskCreateRequest;
import com.bidcollab.agent.dto.AgentTaskListItemResponse;
import com.bidcollab.agent.dto.AgentTaskResponse;
import com.bidcollab.agent.runtime.AgentRunMode;
import com.bidcollab.agent.runtime.ReActAgentRuntime;
import com.bidcollab.agent.runtime.ReActContext;
import com.bidcollab.agent.runtime.AgentRuntimeProperties;
import com.bidcollab.service.CurrentUserService;
import jakarta.persistence.EntityNotFoundException;
import java.util.concurrent.ExecutorService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Agent 任务服务：创建任务、异步执行、查询进度。
 */
@Service
@Slf4j
public class DocumentAgentTaskService {
  private final ReActAgentRuntime runtime;
  private final AgentTaskProgressStore progressStore;
  private final CurrentUserService currentUserService;
  private final AgentRuntimeProperties runtimeProperties;
  private final ExecutorService aiDocumentExecutor;

  public DocumentAgentTaskService(ReActAgentRuntime runtime,
      AgentTaskProgressStore progressStore,
      CurrentUserService currentUserService,
      AgentRuntimeProperties runtimeProperties,
      @Qualifier("aiDocumentExecutor") ExecutorService aiDocumentExecutor) {
    this.runtime = runtime;
    this.progressStore = progressStore;
    this.currentUserService = currentUserService;
    this.runtimeProperties = runtimeProperties;
    this.aiDocumentExecutor = aiDocumentExecutor;
  }

  public AgentTaskResponse createAndRun(AgentTaskCreateRequest request) {
    if (request.getDocumentId() == null) {
      throw new IllegalArgumentException("documentId is required");
    }
    Long operatorId = currentUserService.getCurrentUserId();
    AgentRunMode runMode = AgentRunMode.fromNullable(request.getRunMode());
    int maxIterations = runMode == AgentRunMode.FAST_DRAFT
        ? runtimeProperties.getMaxIterationsFastDraft()
        : runtimeProperties.getMaxIterationsStandard();
    AgentTaskResponse created = progressStore.createTask(
        request.getDocumentId(),
        request.getSectionId(),
        request.getKnowledgeBaseId(),
        runMode.name(),
        request.getRequirement(),
        request.getProjectParams(),
        operatorId,
        maxIterations);
    Long taskId = Long.parseLong(created.getId());
    log.info("[Agent][Task:{}] created, documentId={}, sectionId={}, kbId={}, runMode={}",
        taskId, request.getDocumentId(), request.getSectionId(), request.getKnowledgeBaseId(), runMode);

    ReActContext context = ReActContext.builder()
        .taskId(taskId)
        .operatorId(operatorId)
        .documentId(request.getDocumentId())
        .sectionId(request.getSectionId())
        .knowledgeBaseId(request.getKnowledgeBaseId())
        .runMode(runMode)
        .requirement(request.getRequirement())
        .projectParams(request.getProjectParams())
        .maxIterations(maxIterations)
        .build();

    aiDocumentExecutor.submit(() -> {
      try {
        runtime.run(context);
        progressStore.finishTaskSuccess(taskId, context.getFinalSummary());
        log.info("[Agent][Task:{}] finished successfully", taskId);
      } catch (Exception ex) {
        progressStore.finishTaskFailed(taskId, ex.getMessage());
        log.error("[Agent][Task:{}] failed", taskId, ex);
      }
    });
    return created;
  }

  public AgentTaskResponse getTask(Long taskId) {
    AgentTaskResponse response = progressStore.getTask(taskId);
    if (response == null) {
      throw new EntityNotFoundException("Agent task not found: " + taskId);
    }
    return response;
  }

  public List<AgentTaskListItemResponse> listTasksByDocument(Long documentId, Integer limit) {
    if (documentId == null) {
      throw new IllegalArgumentException("documentId is required");
    }
    int safeLimit = limit == null ? 10 : limit;
    return progressStore.listTasksByDocument(documentId, safeLimit);
  }
}
