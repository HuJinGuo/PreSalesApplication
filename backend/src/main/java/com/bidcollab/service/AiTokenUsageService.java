package com.bidcollab.service;

import com.bidcollab.dto.AiTokenUsageDailyItem;
import com.bidcollab.dto.AiTokenUsageDailyResponse;
import com.bidcollab.dto.AiTokenUsageModelItem;
import com.bidcollab.dto.AiTokenUsageRecordDetailResponse;
import com.bidcollab.dto.AiTokenUsageProviderItem;
import com.bidcollab.dto.AiTokenUsageRecordItem;
import com.bidcollab.dto.AiTokenUsageRecordPageResponse;
import com.bidcollab.entity.AiCallTrace;
import com.bidcollab.entity.AiTokenUsage;
import com.bidcollab.repository.AiCallTraceRepository;
import com.bidcollab.repository.AiTokenUsageRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.Builder;
import lombok.Data;

@Service
public class AiTokenUsageService {
  private final AiTokenUsageRepository repository;
  private final AiCallTraceRepository callTraceRepository;

  public AiTokenUsageService(AiTokenUsageRepository repository, AiCallTraceRepository callTraceRepository) {
    this.repository = repository;
    this.callTraceRepository = callTraceRepository;
  }

  @Data
  @Builder
  public static class UsageRecordCommand {
    private String traceId;
    private String requestType;
    private String provider;
    private String modelName;
    private String scene;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long latencyMs;
    private boolean estimated;
    private boolean success;
    private Integer httpStatus;
    private String errorCode;
    private String errorMessage;
    private String vendorRequestId;
    private String requestPayload;
    private String responsePayload;
    private Long knowledgeBaseId;
    private Long knowledgeDocumentId;
    private Long sectionId;
    private Long aiTaskId;
    private Integer retryCount;
    private Long userId;
  }

  @Transactional
  public void record(UsageRecordCommand command) {
    LocalDate usageDate = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate();
    repository.save(AiTokenUsage.builder()
        .usageDate(usageDate)
        .requestType(safe(command.getRequestType(), "UNKNOWN"))
        .provider(safe(command.getProvider(), "UNKNOWN"))
        .modelName(safe(command.getModelName(), "-"))
        .scene(command.getScene())
        .promptTokens(Math.max(command.getPromptTokens(), 0))
        .completionTokens(Math.max(command.getCompletionTokens(), 0))
        .totalTokens(Math.max(command.getTotalTokens(),
            Math.max(command.getPromptTokens(), 0) + Math.max(command.getCompletionTokens(), 0)))
        .latencyMs(Math.max(command.getLatencyMs(), 0L))
        .estimated(command.isEstimated())
        .success(command.isSuccess())
        .createdBy(command.getUserId())
        .build());

    callTraceRepository.save(AiCallTrace.builder()
        .usageDate(usageDate)
        .traceId(safe(command.getTraceId(), UUID.randomUUID().toString().replace("-", "")))
        .requestType(safe(command.getRequestType(), "UNKNOWN"))
        .provider(safe(command.getProvider(), "UNKNOWN"))
        .modelName(safe(command.getModelName(), "-"))
        .scene(safe(command.getScene(), "-"))
        .promptTokens(Math.max(command.getPromptTokens(), 0))
        .completionTokens(Math.max(command.getCompletionTokens(), 0))
        .totalTokens(Math.max(command.getTotalTokens(),
            Math.max(command.getPromptTokens(), 0) + Math.max(command.getCompletionTokens(), 0)))
        .latencyMs(Math.max(command.getLatencyMs(), 0L))
        .estimated(command.isEstimated())
        .success(command.isSuccess())
        .httpStatus(command.getHttpStatus())
        .errorCode(truncate(command.getErrorCode(), 64))
        .errorMessage(truncate(command.getErrorMessage(), 2000))
        .vendorRequestId(truncate(command.getVendorRequestId(), 128))
        .knowledgeBaseId(command.getKnowledgeBaseId())
        .knowledgeDocumentId(command.getKnowledgeDocumentId())
        .sectionId(command.getSectionId())
        .aiTaskId(command.getAiTaskId())
        .retryCount(command.getRetryCount() == null ? 0 : Math.max(command.getRetryCount(), 0))
        .requestPayload(truncate(command.getRequestPayload(), 8000))
        .responsePayload(truncate(command.getResponsePayload(), 12000))
        .createdBy(command.getUserId())
        .build());
  }

  @Transactional(readOnly = true)
  public AiTokenUsageDailyResponse daily(LocalDate startDate, LocalDate endDate) {
    LocalDate end = endDate == null ? LocalDate.now() : endDate;
    LocalDate start = startDate == null ? end.minusDays(13) : startDate;
    if (start.isAfter(end)) {
      LocalDate tmp = start;
      start = end;
      end = tmp;
    }
    final LocalDate qStart = start;
    final LocalDate qEnd = end;

    List<AiTokenUsageDailyItem> daily = repository.aggregateDaily(start, end).stream()
        .map(item -> AiTokenUsageDailyItem.builder()
            .usageDate(item.getUsageDate())
            .promptTokens(z(item.getPromptTokens()))
            .completionTokens(z(item.getCompletionTokens()))
            .totalTokens(z(item.getTotalTokens()))
            .requestCount(z(item.getRequestCount()))
            .successCount(z(item.getSuccessCount()))
            .build())
        .toList();

    List<AiTokenUsageProviderItem> providers = repository.aggregateByProvider(start, end).stream()
        .map(item -> AiTokenUsageProviderItem.builder()
            .provider(safe(item.getProvider(), "UNKNOWN"))
            .promptTokens(z(item.getPromptTokens()))
            .completionTokens(z(item.getCompletionTokens()))
            .totalTokens(z(item.getTotalTokens()))
            .requestCount(z(item.getRequestCount()))
            .build())
        .toList();

    List<AiTokenUsageModelItem> models = repository.aggregateByModel(start, end).stream()
        .map(item -> AiTokenUsageModelItem.builder()
            .provider(safe(item.getProvider(), "UNKNOWN"))
            .modelName(safe(item.getModelName(), "-"))
            .promptTokens(z(item.getPromptTokens()))
            .completionTokens(z(item.getCompletionTokens()))
            .totalTokens(z(item.getTotalTokens()))
            .requestCount(z(item.getRequestCount()))
            .build())
        .toList();

    long totalPrompt = daily.stream().mapToLong(AiTokenUsageDailyItem::getPromptTokens).sum();
    long totalCompletion = daily.stream().mapToLong(AiTokenUsageDailyItem::getCompletionTokens).sum();
    long total = daily.stream().mapToLong(AiTokenUsageDailyItem::getTotalTokens).sum();
    long totalRequests = daily.stream().mapToLong(AiTokenUsageDailyItem::getRequestCount).sum();
    long successRequests = daily.stream().mapToLong(AiTokenUsageDailyItem::getSuccessCount).sum();
    return AiTokenUsageDailyResponse.builder()
        .startDate(start)
        .endDate(end)
        .totalPromptTokens(totalPrompt)
        .totalCompletionTokens(totalCompletion)
        .totalTokens(total)
        .totalRequests(totalRequests)
        .successRequests(successRequests)
        .daily(daily)
        .providers(providers)
        .models(models)
        .build();
  }

  @Transactional(readOnly = true)
  public AiTokenUsageRecordPageResponse records(LocalDate startDate, LocalDate endDate, Integer page, Integer size,
      Long knowledgeBaseId, Long knowledgeDocumentId, Boolean success) {
    LocalDate end = endDate == null ? LocalDate.now() : endDate;
    boolean scopedBizQuery = knowledgeBaseId != null || knowledgeDocumentId != null;
    LocalDate start = startDate == null
        ? (scopedBizQuery ? end.minusDays(3650) : end.minusDays(13))
        : startDate;
    if (start.isAfter(end)) {
      LocalDate tmp = start;
      start = end;
      end = tmp;
    }
    final LocalDate qStart = start;
    final LocalDate qEnd = end;
    int pageNo = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);
    Specification<AiCallTrace> spec = (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      predicates.add(cb.between(root.get("usageDate"), qStart, qEnd));
      if (knowledgeBaseId != null) {
        predicates.add(cb.equal(root.get("knowledgeBaseId"), knowledgeBaseId));
      }
      if (knowledgeDocumentId != null) {
        predicates.add(cb.equal(root.get("knowledgeDocumentId"), knowledgeDocumentId));
      }
      if (success != null) {
        predicates.add(cb.equal(root.get("success"), success));
      }
      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
    Page<AiCallTrace> p = callTraceRepository.findAll(spec,
        PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));

    List<AiTokenUsageRecordItem> records = p.getContent().stream().map(row -> AiTokenUsageRecordItem.builder()
        .id(row.getId())
        .traceId(row.getTraceId())
        .createdAt(row.getCreatedAt())
        .requestType(row.getRequestType())
        .provider(row.getProvider())
        .modelName(row.getModelName())
        .scene(row.getScene())
        .promptTokens(row.getPromptTokens() == null ? 0 : row.getPromptTokens())
        .completionTokens(row.getCompletionTokens() == null ? 0 : row.getCompletionTokens())
        .totalTokens(row.getTotalTokens() == null ? 0 : row.getTotalTokens())
        .latencyMs(row.getLatencyMs() == null ? 0L : row.getLatencyMs())
        .estimated(Boolean.TRUE.equals(row.getEstimated()))
        .success(Boolean.TRUE.equals(row.getSuccess()))
        .httpStatus(row.getHttpStatus())
        .errorCode(row.getErrorCode())
        .errorMessage(row.getErrorMessage())
        .knowledgeBaseId(row.getKnowledgeBaseId())
        .knowledgeDocumentId(row.getKnowledgeDocumentId())
        .sectionId(row.getSectionId())
        .aiTaskId(row.getAiTaskId())
        .retryCount(row.getRetryCount() == null ? 0 : row.getRetryCount())
        .createdBy(row.getCreatedBy())
        .build()).toList();

    return AiTokenUsageRecordPageResponse.builder()
        .total(p.getTotalElements())
        .page(pageNo)
        .size(pageSize)
        .records(records)
        .build();
  }

  @Transactional(readOnly = true)
  public AiTokenUsageRecordDetailResponse recordDetail(Long id) {
    AiCallTrace row = callTraceRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    return AiTokenUsageRecordDetailResponse.builder()
        .id(row.getId())
        .traceId(row.getTraceId())
        .createdAt(row.getCreatedAt())
        .requestType(row.getRequestType())
        .provider(row.getProvider())
        .modelName(row.getModelName())
        .scene(row.getScene())
        .promptTokens(row.getPromptTokens() == null ? 0 : row.getPromptTokens())
        .completionTokens(row.getCompletionTokens() == null ? 0 : row.getCompletionTokens())
        .totalTokens(row.getTotalTokens() == null ? 0 : row.getTotalTokens())
        .latencyMs(row.getLatencyMs() == null ? 0L : row.getLatencyMs())
        .estimated(Boolean.TRUE.equals(row.getEstimated()))
        .success(Boolean.TRUE.equals(row.getSuccess()))
        .httpStatus(row.getHttpStatus())
        .errorCode(row.getErrorCode())
        .errorMessage(row.getErrorMessage())
        .vendorRequestId(row.getVendorRequestId())
        .retryCount(row.getRetryCount() == null ? 0 : row.getRetryCount())
        .knowledgeBaseId(row.getKnowledgeBaseId())
        .knowledgeDocumentId(row.getKnowledgeDocumentId())
        .sectionId(row.getSectionId())
        .aiTaskId(row.getAiTaskId())
        .requestPayload(row.getRequestPayload())
        .responsePayload(row.getResponsePayload())
        .createdBy(row.getCreatedBy())
        .build();
  }

  private String safe(String text, String fallback) {
    if (text == null || text.isBlank()) {
      return fallback;
    }
    return text.trim();
  }

  private long z(Long value) {
    return value == null ? 0L : value;
  }

  private String truncate(String text, int maxLen) {
    if (text == null) {
      return null;
    }
    if (text.length() <= maxLen) {
      return text;
    }
    return text.substring(0, maxLen);
  }
}
