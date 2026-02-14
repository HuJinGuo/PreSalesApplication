package com.bidcollab.controller;

import com.bidcollab.dto.AiAssistantAskRequest;
import com.bidcollab.dto.AiAssistantAskResponse;
import com.bidcollab.dto.AiRewriteRequest;
import com.bidcollab.dto.AiTaskResponse;
import com.bidcollab.service.AiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
  private final AiService aiService;

  public AiController(AiService aiService) {
    this.aiService = aiService;
  }

  @PostMapping("/rewrite")
  public AiTaskResponse rewrite(@Valid @RequestBody AiRewriteRequest request) {
    return aiService.rewrite(request);
  }

  @PostMapping("/ask")
  public AiAssistantAskResponse ask(@Valid @RequestBody AiAssistantAskRequest request) {
    return aiService.ask(request);
  }

  @GetMapping("/tasks/{taskId}")
  public AiTaskResponse task(@PathVariable("taskId") Long taskId) {
    return aiService.getTask(taskId);
  }
}
