package com.bidcollab.controller;

import com.bidcollab.dto.AiAssistantAskRequest;
import com.bidcollab.dto.AiAssistantAskResponse;
import com.bidcollab.dto.AiDocumentAutoWriteRequest;
import com.bidcollab.dto.AiRewriteRequest;
import com.bidcollab.dto.AiTaskResponse;
import com.bidcollab.dto.AiTokenUsageDailyResponse;
import com.bidcollab.dto.AiTokenUsageRecordDetailResponse;
import com.bidcollab.dto.AiTokenUsageRecordPageResponse;
import com.bidcollab.service.AiService;
import com.bidcollab.service.AiTokenUsageService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
  private final AiService aiService;
  private final AiTokenUsageService aiTokenUsageService;

  public AiController(AiService aiService, AiTokenUsageService aiTokenUsageService) {
    this.aiService = aiService;
    this.aiTokenUsageService = aiTokenUsageService;
  }

  @PostMapping("/rewrite")
  public AiTaskResponse rewrite(@Valid @RequestBody AiRewriteRequest request) {
    return aiService.rewrite(request);
  }

  @PostMapping("/ask")
  public AiAssistantAskResponse ask(@Valid @RequestBody AiAssistantAskRequest request) {
    return aiService.ask(request);
  }

  @PostMapping("/document-auto-write")
  public AiTaskResponse autoWriteDocument(@Valid @RequestBody AiDocumentAutoWriteRequest request) {
    return aiService.autoWriteDocument(request);
  }

  @GetMapping("/tasks/{taskId}")
  public AiTaskResponse task(@PathVariable("taskId") Long taskId) {
    return aiService.getTask(taskId);
  }

  @GetMapping("/token-usage/daily")
  public AiTokenUsageDailyResponse dailyTokenUsage(
      @RequestParam(name = "startDate", required = false) LocalDate startDate,
      @RequestParam(name = "endDate", required = false) LocalDate endDate) {
    return aiTokenUsageService.daily(startDate, endDate);
  }

  @GetMapping("/token-usage/records")
  public AiTokenUsageRecordPageResponse tokenUsageRecords(
      @RequestParam(name = "startDate", required = false) LocalDate startDate,
      @RequestParam(name = "endDate", required = false) LocalDate endDate,
      @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
      @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
      @RequestParam(name = "knowledgeBaseId", required = false) Long knowledgeBaseId,
      @RequestParam(name = "knowledgeDocumentId", required = false) Long knowledgeDocumentId,
      @RequestParam(name = "success", required = false) Boolean success) {
    return aiTokenUsageService.records(startDate, endDate, page, size, knowledgeBaseId, knowledgeDocumentId, success);
  }

  @GetMapping("/token-usage/records/{id}")
  public AiTokenUsageRecordDetailResponse tokenUsageRecordDetail(@PathVariable("id") Long id) {
    return aiTokenUsageService.recordDetail(id);
  }
}
