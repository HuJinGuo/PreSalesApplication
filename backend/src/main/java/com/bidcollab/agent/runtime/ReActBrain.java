package com.bidcollab.agent.runtime;

import com.bidcollab.agent.gateway.ModelGateway;
import com.bidcollab.agent.service.AgentDocumentOpsService;
import com.bidcollab.agent.tool.DocumentAgentTools;
import com.bidcollab.util.StringUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReActBrain {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ModelGateway modelGateway;
  private final DocumentAgentTools documentAgentTools;
  private final AgentDocumentOpsService opsService;

  public ReActBrain(ModelGateway modelGateway, DocumentAgentTools documentAgentTools,
      AgentDocumentOpsService opsService) {
    this.modelGateway = modelGateway;
    this.documentAgentTools = documentAgentTools;
    this.opsService = opsService;
  }

  public ReActDecision decide(ReActContext context) {
    String toolsDesc = buildToolList();
    String toolNames = buildToolNames();
    String systemPrompt = """
        你是文档编写 Agent 的决策大脑，必须遵循 ReAct：
        - 先思考当前缺什么，再选择一个最合适的工具执行；
        - 每轮最多执行一个工具；
        - 当目标已完成时输出 FINISH。

        你只能返回 JSON：
        {
          "action":"TOOL|FINISH",
          "tool":"%s",
          "args":{},
          "reason":"简要原因",
          "finalSummary":"仅 FINISH 时可填"
        }

        可用工具：
        %s

        决策约束：
        1) STANDARD 模式推荐链路：检索 -> 大纲 -> 落库 -> 逐章节写作 -> FINISH
        2) FAST_DRAFT 模式推荐链路：检索 -> 整篇草稿 -> 拆章落库 -> FINISH
        3) 不要重复执行已完成且无必要重试的步骤。
        """
        .formatted(toolNames, toolsDesc);

    String userPrompt = """
        任务信息：
        - runMode: %s
        - documentId: %s
        - sectionId: %s
        - requirement: %s
        - projectParams: %s

        当前状态：
        - iteration: %d/%d
        - hasOutline: %s
        - hasGlobalHits: %s
        - outlinePersisted: %s
        - hasFullDraft: %s
        - writtenSectionCount: %d
        - sectionProgress: %d/%d
        - hasPendingSections: %s

        最近观察（倒序最多6条）：
        %s
        """.formatted(
        context.getRunMode(),
        context.getDocumentId(),
        context.getSectionId(),
        StringUtil.safe(context.getRequirement()),
        StringUtil.safe(context.getProjectParams()),
        context.getIteration(),
        context.getMaxIterations(),
        opsService.hasOutline(context),
        opsService.hasGlobalHits(context),
        opsService.isOutlinePersisted(context),
        opsService.hasFullDraft(context),
        opsService.writtenSectionCount(context),
        opsService.currentSectionCursor(context),
        opsService.totalSectionCount(context),
        opsService.hasPendingSections(context),
        latestObservations(context.getObservations(), 6));

    String raw = modelGateway.chat(systemPrompt, userPrompt);
    ReActDecision parsed = parse(raw);
    if (parsed != null && parsed.getAction() != null) {
      return parsed;
    }
    return fallback(context);
  }

  private String buildToolList() {
    StringBuilder sb = new StringBuilder();
    for (Method m : documentAgentTools.getClass().getDeclaredMethods()) {
      if (m.isAnnotationPresent(Tool.class)) {
        Tool annotation = m.getAnnotation(Tool.class);
        sb.append("- ").append(m.getName()).append(": ").append(annotation.value()).append('\n');
      }
    }
    return sb.toString().trim();
  }

  private String buildToolNames() {
    java.util.List<String> names = new java.util.ArrayList<>();
    for (Method m : documentAgentTools.getClass().getDeclaredMethods()) {
      if (m.isAnnotationPresent(Tool.class)) {
        names.add(m.getName());
      }
    }
    return String.join("|", names);
  }

  private String latestObservations(List<String> observations, int max) {
    if (observations == null || observations.isEmpty()) {
      return "- (暂无)";
    }
    int from = Math.max(0, observations.size() - max);
    List<String> latest = new ArrayList<>(observations.subList(from, observations.size()));
    java.util.Collections.reverse(latest);
    StringBuilder sb = new StringBuilder();
    for (String observation : latest) {
      sb.append("- ").append(observation).append('\n');
    }
    return sb.toString().trim();
  }

  private ReActDecision parse(String raw) {
    JsonNode root = readJson(raw);
    if (root == null) {
      return null;
    }
    String actionRaw = StringUtil.safe(root.path("action").asText(""));
    ReActDecision.Action action;
    try {
      action = ReActDecision.Action.valueOf(actionRaw.toUpperCase());
    } catch (Exception ex) {
      return null;
    }
    String tool = StringUtil.safe(root.path("tool").asText("")).toLowerCase();
    Map<String, Object> args = parseArgs(root.path("args"));
    String reason = StringUtil.safe(root.path("reason").asText(""));
    String finalSummary = StringUtil.safe(root.path("finalSummary").asText(""));
    return ReActDecision.builder()
        .action(action)
        .tool(tool)
        .args(args)
        .reason(reason)
        .finalSummary(finalSummary)
        .build();
  }

  private JsonNode readJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String text = raw.trim();
    if (text.startsWith("```")) {
      int firstLine = text.indexOf('\n');
      int endFence = text.lastIndexOf("```");
      if (firstLine > 0 && endFence > firstLine) {
        text = text.substring(firstLine + 1, endFence).trim();
      }
    }
    try {
      return MAPPER.readTree(text);
    } catch (Exception ignored) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseArgs(JsonNode node) {
    if (node == null || !node.isObject()) {
      return new LinkedHashMap<>();
    }
    return MAPPER.convertValue(node, Map.class);
  }

  private ReActDecision fallback(ReActContext context) {
    if (context.getRunMode() == AgentRunMode.FAST_DRAFT) {
      if (!opsService.hasGlobalHits(context)) {
        return ReActDecision.builder()
            .action(ReActDecision.Action.TOOL)
            .tool("retrieve_knowledge")
            .reason("fallback: retrieve first")
            .build();
      }
      if (!opsService.hasFullDraft(context)) {
        return ReActDecision.builder()
            .action(ReActDecision.Action.TOOL)
            .tool("compose_full_draft")
            .reason("fallback: compose full draft")
            .build();
      }
      if (opsService.writtenSectionCount(context) <= 0) {
        return ReActDecision.builder()
            .action(ReActDecision.Action.TOOL)
            .tool("split_persist_draft")
            .reason("fallback: persist draft")
            .build();
      }
      return ReActDecision.builder()
          .action(ReActDecision.Action.FINISH)
          .finalSummary("任务完成：已按 FAST_DRAFT 写入章节内容")
          .reason("fallback finish")
          .build();
    }
    if (!opsService.hasGlobalHits(context)) {
      return ReActDecision.builder()
          .action(ReActDecision.Action.TOOL)
          .tool("retrieve_knowledge")
          .reason("fallback: retrieve for outline")
          .build();
    }
    if (!opsService.hasOutline(context)) {
      return ReActDecision.builder()
          .action(ReActDecision.Action.TOOL)
          .tool("generate_outline")
          .reason("fallback: generate outline")
          .build();
    }
    if (!opsService.isOutlinePersisted(context)) {
      return ReActDecision.builder()
          .action(ReActDecision.Action.TOOL)
          .tool("persist_outline")
          .reason("fallback: persist outline")
          .build();
    }
    if (opsService.writtenSectionCount(context) <= 0) {
      return ReActDecision.builder()
          .action(ReActDecision.Action.TOOL)
          .tool("compose_sections")
          .args(buildComposeArgs(context))
          .reason("fallback: compose sections")
          .build();
    }
    if (opsService.hasPendingSections(context)) {
      return ReActDecision.builder()
          .action(ReActDecision.Action.TOOL)
          .tool("compose_sections")
          .args(buildComposeArgs(context))
          .reason("fallback: continue compose sections")
          .build();
    }
    return ReActDecision.builder()
        .action(ReActDecision.Action.FINISH)
        .finalSummary("任务完成：已按 STANDARD 写入章节内容")
        .reason("fallback finish")
        .build();
  }

  private Map<String, Object> buildComposeArgs(ReActContext context) {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("batchSize", 2);
    args.put("timeoutMs", 180000);
    args.put("maxRetries", 0);
    args.put("cursor", opsService.currentSectionCursor(context));
    return args;
  }
}
