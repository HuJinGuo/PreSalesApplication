package com.bidcollab.agent.task;

import com.bidcollab.agent.dto.AgentTaskCreateRequest;
import com.bidcollab.agent.dto.AgentTaskListItemResponse;
import com.bidcollab.agent.dto.AgentTaskResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-tasks")
@Slf4j
public class DocumentAgentTaskController {
  private final DocumentAgentTaskService taskService;

  public DocumentAgentTaskController(DocumentAgentTaskService taskService) {
    this.taskService = taskService;
  }

  @PostMapping
  public AgentTaskResponse create(@Valid @RequestBody AgentTaskCreateRequest request) {
    log.info("[Agent][API] create task: documentId={}, sectionId={}, kbId={}, runMode={}",
        request.getDocumentId(), request.getSectionId(), request.getKnowledgeBaseId(), request.getRunMode());
    return taskService.createAndRun(request);
  }

  @GetMapping("/{taskId}")
  public AgentTaskResponse detail(@PathVariable("taskId") Long taskId) {
    return taskService.getTask(taskId);
  }

  @GetMapping
  public List<AgentTaskListItemResponse> listByDocument(
      @RequestParam("documentId") Long documentId,
      @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit) {
    return taskService.listTasksByDocument(documentId, limit);
  }
}
