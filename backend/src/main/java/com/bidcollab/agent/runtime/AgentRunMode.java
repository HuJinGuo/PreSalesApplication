package com.bidcollab.agent.runtime;

public enum AgentRunMode {
  STANDARD,
  FAST_DRAFT;

  public static AgentRunMode fromNullable(String raw) {
    if (raw == null || raw.isBlank()) {
      return STANDARD;
    }
    try {
      return AgentRunMode.valueOf(raw.trim().toUpperCase());
    } catch (Exception ignored) {
      return STANDARD;
    }
  }
}
