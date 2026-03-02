package com.bidcollab.agent.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * ReAct 运行时上下文。
 */
@Data
@Builder
public class ReActContext {
  private Long taskId;
  private Long operatorId;
  private Long documentId;
  private Long sectionId;
  private Long knowledgeBaseId;
  private AgentRunMode runMode;
  private String requirement;
  private String projectParams;
  private int maxIterations;

  @Builder.Default
  private int iteration = 0;

  @Builder.Default
  private List<String> observations = new ArrayList<>();

  @Builder.Default
  private Map<String, Object> memory = new LinkedHashMap<>();

  private String finalSummary;

  public void addObservation(String text) {
    if (text == null || text.isBlank()) {
      return;
    }
    observations.add(text.trim());
    if (observations.size() > 30) {
      observations = new ArrayList<>(observations.subList(observations.size() - 30, observations.size()));
    }
  }
}
