package com.bidcollab.service;

import com.bidcollab.dto.AiTokenUsageDailyItem;
import com.bidcollab.dto.AiTokenUsageDailyResponse;
import com.bidcollab.dto.AiTokenUsageModelItem;
import com.bidcollab.dto.AiTokenUsageProviderItem;
import com.bidcollab.dto.AiTokenUsageRecordItem;
import com.bidcollab.dto.AiTokenUsageRecordPageResponse;
import com.bidcollab.entity.AiTokenUsage;
import com.bidcollab.repository.AiTokenUsageRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiTokenUsageService {
  private final AiTokenUsageRepository repository;

  public AiTokenUsageService(AiTokenUsageRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void record(String requestType,
      String provider,
      String modelName,
      String scene,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      long latencyMs,
      boolean estimated,
      boolean success,
      Long userId) {
    LocalDate usageDate = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate();
    repository.save(AiTokenUsage.builder()
        .usageDate(usageDate)
        .requestType(safe(requestType, "UNKNOWN"))
        .provider(safe(provider, "UNKNOWN"))
        .modelName(safe(modelName, "-"))
        .scene(scene)
        .promptTokens(Math.max(promptTokens, 0))
        .completionTokens(Math.max(completionTokens, 0))
        .totalTokens(Math.max(totalTokens, Math.max(promptTokens, 0) + Math.max(completionTokens, 0)))
        .latencyMs(Math.max(latencyMs, 0L))
        .estimated(estimated)
        .success(success)
        .createdBy(userId)
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
  public AiTokenUsageRecordPageResponse records(LocalDate startDate, LocalDate endDate, Integer page, Integer size) {
    LocalDate end = endDate == null ? LocalDate.now() : endDate;
    LocalDate start = startDate == null ? end.minusDays(13) : startDate;
    if (start.isAfter(end)) {
      LocalDate tmp = start;
      start = end;
      end = tmp;
    }
    int pageNo = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);
    Page<AiTokenUsage> p = repository.findByUsageDateBetweenOrderByCreatedAtDesc(
        start, end, PageRequest.of(pageNo - 1, pageSize));

    List<AiTokenUsageRecordItem> records = p.getContent().stream().map(row -> AiTokenUsageRecordItem.builder()
        .id(row.getId())
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
        .createdBy(row.getCreatedBy())
        .build()).toList();

    return AiTokenUsageRecordPageResponse.builder()
        .total(p.getTotalElements())
        .page(pageNo)
        .size(pageSize)
        .records(records)
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
}
