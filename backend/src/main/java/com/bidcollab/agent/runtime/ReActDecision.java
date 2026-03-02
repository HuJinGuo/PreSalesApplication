package com.bidcollab.agent.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Brain 每轮决策结果。
 */
@Data
@Builder
public class ReActDecision {
  public enum Action {
    TOOL,
    FINISH
  }

  private Action action;
  private String tool;
  @Builder.Default
  private Map<String, Object> args = new LinkedHashMap<>();
  private String reason;
  private String finalSummary;
}
