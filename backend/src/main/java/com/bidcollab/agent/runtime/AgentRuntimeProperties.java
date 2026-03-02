package com.bidcollab.agent.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.agent.runtime")
public class AgentRuntimeProperties {
  private int maxIterationsStandard = 14;
  private int maxIterationsFastDraft = 10;
  private long toolTimeoutMs = 90000;
  private int toolMaxRetries = 1;
  private long toolRetryBackoffMs = 800;
}
