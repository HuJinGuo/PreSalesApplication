package com.bidcollab.repository;

import com.bidcollab.entity.AiTokenUsage;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiTokenUsageRepository extends JpaRepository<AiTokenUsage, Long> {

  interface DailyAggProjection {
    LocalDate getUsageDate();

    Long getPromptTokens();

    Long getCompletionTokens();

    Long getTotalTokens();

    Long getRequestCount();

    Long getSuccessCount();
  }

  interface ProviderAggProjection {
    String getProvider();

    Long getPromptTokens();

    Long getCompletionTokens();

    Long getTotalTokens();

    Long getRequestCount();
  }

  interface ModelAggProjection {
    String getModelName();

    String getProvider();

    Long getPromptTokens();

    Long getCompletionTokens();

    Long getTotalTokens();

    Long getRequestCount();
  }

  @Query(value = """
      SELECT usage_date AS usageDate,
             SUM(prompt_tokens) AS promptTokens,
             SUM(completion_tokens) AS completionTokens,
             SUM(total_tokens) AS totalTokens,
             COUNT(*) AS requestCount,
             SUM(CASE WHEN is_success = 1 THEN 1 ELSE 0 END) AS successCount
      FROM ai_token_usage
      WHERE usage_date BETWEEN :startDate AND :endDate
      GROUP BY usage_date
      ORDER BY usage_date ASC
      """, nativeQuery = true)
  List<DailyAggProjection> aggregateDaily(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

  @Query(value = """
      SELECT provider AS provider,
             SUM(prompt_tokens) AS promptTokens,
             SUM(completion_tokens) AS completionTokens,
             SUM(total_tokens) AS totalTokens,
             COUNT(*) AS requestCount
      FROM ai_token_usage
      WHERE usage_date BETWEEN :startDate AND :endDate
      GROUP BY provider
      ORDER BY totalTokens DESC
      """, nativeQuery = true)
  List<ProviderAggProjection> aggregateByProvider(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

  @Query(value = """
      SELECT model_name AS modelName,
             provider AS provider,
             SUM(prompt_tokens) AS promptTokens,
             SUM(completion_tokens) AS completionTokens,
             SUM(total_tokens) AS totalTokens,
             COUNT(*) AS requestCount
      FROM ai_token_usage
      WHERE usage_date BETWEEN :startDate AND :endDate
      GROUP BY model_name, provider
      ORDER BY totalTokens DESC
      """, nativeQuery = true)
  List<ModelAggProjection> aggregateByModel(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

  Page<AiTokenUsage> findByUsageDateBetweenOrderByCreatedAtDesc(LocalDate startDate, LocalDate endDate, Pageable pageable);
}
