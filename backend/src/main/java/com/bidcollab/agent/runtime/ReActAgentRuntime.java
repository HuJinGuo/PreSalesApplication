package com.bidcollab.agent.runtime;

import com.bidcollab.agent.task.AgentTaskProgressStore;
import com.bidcollab.agent.tool.DocumentAgentTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReActAgentRuntime {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final ReActBrain brain;
  private final DocumentAgentTools documentAgentTools;
  private final AgentTaskProgressStore progressStore;
  private final AgentRuntimeProperties runtimeProperties;
  private final ExecutorService toolExecutor;

  private record ToolExecutionResult(boolean isSuccess, String summary, String error) {
    static ToolExecutionResult success(String s) {
      return new ToolExecutionResult(true, s, null);
    }

    static ToolExecutionResult failure(String e) {
      return new ToolExecutionResult(false, null, e);
    }
  }

  public ReActAgentRuntime(ReActBrain brain,
      DocumentAgentTools documentAgentTools,
      AgentTaskProgressStore progressStore,
      AgentRuntimeProperties runtimeProperties,
      @Qualifier("agentToolExecutor") ExecutorService toolExecutor) {
    this.brain = brain;
    this.documentAgentTools = documentAgentTools;
    this.progressStore = progressStore;
    this.runtimeProperties = runtimeProperties;
    this.toolExecutor = toolExecutor;
  }

  public void run(ReActContext context) {
    int maxIterations = context.getMaxIterations() > 0
        ? context.getMaxIterations()
        : (context.getRunMode() == AgentRunMode.FAST_DRAFT
            ? runtimeProperties.getMaxIterationsFastDraft()
            : runtimeProperties.getMaxIterationsStandard());
    context.setMaxIterations(maxIterations);

    int continuousErrors = 0;
    for (int i = 1; i <= maxIterations; i++) {
      context.setIteration(i);
      ReActDecision decision = brain.decide(context);
      log.info("[Agent][Task:{}] loop={}, decision={}/{} reason={}",
          context.getTaskId(), i, decision.getAction(), decision.getTool(), decision.getReason());

      Map<String, Object> decisionArgs = decision.getArgs() == null ? Map.of() : decision.getArgs();
      if (decision.getAction() == ReActDecision.Action.FINISH) {
        Long stepId = progressStore.startStep(
            context.getTaskId(),
            i,
            "LOOP_" + i,
            stepName(decision),
            stepType(decision),
            null,
            decision.getReason(),
            decisionArgs,
            null,
            0L);
        String finalSummary = (decision.getFinalSummary() == null || decision.getFinalSummary().isBlank())
            ? "任务完成"
            : decision.getFinalSummary();
        context.setFinalSummary(finalSummary);
        context.addObservation("FINISH: " + finalSummary);
        progressStore.finishStep(stepId, true, finalSummary, null, 0);
        return;
      }

      if (decision.getAction() != ReActDecision.Action.TOOL || decision.getTool() == null
          || decision.getTool().isBlank()) {
        Long stepId = progressStore.startStep(
            context.getTaskId(),
            i,
            "LOOP_" + i,
            stepName(decision),
            stepType(decision),
            null,
            decision.getReason(),
            decisionArgs,
            null,
            0L);
        continuousErrors++;
        String err = "Invalid decision: tool action required";
        context.addObservation("ERROR: " + err);
        progressStore.finishStep(stepId, false, null, err, 0);
        if (continuousErrors >= 2) {
          throw new IllegalStateException(err);
        }
        continue;
      }

      String toolName = normalizeToolName(decision.getTool());
      long timeoutMs = resolveTimeoutMs(decisionArgs);
      int maxRetries = resolveMaxRetries(decisionArgs);
      String idempotencyKey = supportsIdempotency(toolName)
          ? buildIdempotencyKey(context.getTaskId(), toolName, decisionArgs)
          : null;
      Long stepId = progressStore.startStep(
          context.getTaskId(),
          i,
          "LOOP_" + i,
          toolName,
          "TOOL",
          toolName,
          decision.getReason(),
          decisionArgs,
          idempotencyKey,
          timeoutMs);

      if (idempotencyKey != null && progressStore.hasSuccessfulIdempotencyStep(context.getTaskId(), idempotencyKey)) {
        String observation = "TOOL[" + toolName + "] skipped by idempotency key";
        context.addObservation(observation);
        progressStore.finishStep(stepId, true, observation, null, 0);
        continuousErrors = 0;
        continue;
      }

      ToolExecutionResult result = null;
      int retryCount = 0;
      for (int attempt = 0; attempt <= maxRetries; attempt++) {
        retryCount = attempt;
        result = executeToolWithTimeout(toolName, context, decisionArgs, timeoutMs);
        if (result.isSuccess()) {
          break;
        }
        if (attempt < maxRetries) {
          sleepBackoff(attempt);
        }
      }
      if (result == null) {
        result = ToolExecutionResult.failure("tool execution returned null");
      }
      if (result.isSuccess()) {
        continuousErrors = 0;
        String observation = "TOOL[" + toolName + "] " + result.summary();
        context.addObservation(observation);
        progressStore.finishStep(stepId, true, observation, null, retryCount);
      } else {
        continuousErrors++;
        String error = result.error() == null || result.error().isBlank() ? "tool execution failed" : result.error();
        context.addObservation("TOOL[" + toolName + "] FAILED: " + error);
        progressStore.finishStep(stepId, false, null, error, retryCount);
        if (continuousErrors >= 3) {
          throw new IllegalStateException("连续工具失败，任务终止: " + error);
        }
      }
    }
    throw new IllegalStateException("达到最大迭代次数仍未完成");
  }

  private String stepName(ReActDecision decision) {
    if (decision.getAction() == ReActDecision.Action.FINISH) {
      return "完成任务";
    }
    return decision.getTool();
  }

  private String stepType(ReActDecision decision) {
    return decision.getAction() == ReActDecision.Action.FINISH ? "FINISH" : "TOOL";
  }

  private String normalizeToolName(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.trim().toLowerCase().replace(' ', '_');
  }

  private ToolExecutionResult executeToolWithTimeout(String toolName, ReActContext context, Map<String, Object> args,
      long timeoutMs) {
    CompletableFuture<ToolExecutionResult> future = null;
    try {
      future = CompletableFuture.supplyAsync(() -> {
        try {
          Method m = documentAgentTools.getClass().getDeclaredMethod(toolName, ReActContext.class, Map.class);
          Object res = m.invoke(documentAgentTools, context, args);
          return ToolExecutionResult.success(res == null ? "OK" : res.toString());
        } catch (NoSuchMethodException e) {
          return ToolExecutionResult.failure("Unknown tool: " + toolName);
        } catch (Exception e) {
          String err = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
          return ToolExecutionResult.failure(err);
        }
      }, toolExecutor);
      return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.TimeoutException ex) {
      if (future != null) {
        future.cancel(true);
      }
      return ToolExecutionResult.failure("tool timeout: " + timeoutMs + "ms");
    } catch (Exception ex) {
      if (future != null) {
        future.cancel(true);
      }
      return ToolExecutionResult.failure(ex.getMessage());
    }
  }

  private boolean supportsIdempotency(String toolName) {
    return !"compose_sections".equals(toolName);
  }

  private void sleepBackoff(int attempt) {
    long base = Math.max(100L, runtimeProperties.getToolRetryBackoffMs());
    long sleep = Math.min(5000L, base * (attempt + 1));
    try {
      Thread.sleep(sleep);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private long resolveTimeoutMs(Map<String, Object> args) {
    Object v = args.get("timeoutMs");
    if (v instanceof Number n) {
      return Math.max(5000L, n.longValue());
    }
    if (v instanceof String s) {
      try {
        return Math.max(5000L, Long.parseLong(s.trim()));
      } catch (Exception ignored) {
      }
    }
    return Math.max(5000L, runtimeProperties.getToolTimeoutMs());
  }

  private int resolveMaxRetries(Map<String, Object> args) {
    Object v = args.get("maxRetries");
    if (v instanceof Number n) {
      return Math.max(0, Math.min(5, n.intValue()));
    }
    if (v instanceof String s) {
      try {
        return Math.max(0, Math.min(5, Integer.parseInt(s.trim())));
      } catch (Exception ignored) {
      }
    }
    return Math.max(0, Math.min(5, runtimeProperties.getToolMaxRetries()));
  }

  private String buildIdempotencyKey(Long taskId, String toolName, Map<String, Object> args) {
    try {
      String argsJson = MAPPER.writeValueAsString(args == null ? Map.of() : args);
      String source = taskId + "|" + toolName + "|" + argsJson;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception ex) {
      return taskId + "-" + toolName;
    }
  }
}
