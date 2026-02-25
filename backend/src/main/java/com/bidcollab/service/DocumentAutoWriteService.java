package com.bidcollab.service;

import com.bidcollab.ai.AiClient;
import com.bidcollab.dto.AiDocumentAutoWriteRequest;
import com.bidcollab.dto.AiTaskResponse;
import com.bidcollab.dto.KnowledgeSearchRequest;
import com.bidcollab.dto.KnowledgeSearchResult;
import com.bidcollab.entity.AiTask;
import com.bidcollab.entity.Document;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionVersion;
import com.bidcollab.enums.AiTaskStatus;
import com.bidcollab.enums.SectionSourceType;
import com.bidcollab.repository.AiTaskRepository;
import com.bidcollab.repository.DocumentRepository;
import com.bidcollab.repository.SectionRepository;
import com.bidcollab.repository.SectionVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentAutoWriteService {
  // [AI-READ] 文档自动编写专用服务：
  // 负责“创建任务 -> 异步逐章节生成 -> 回填章节正文 -> 写入引用片段 -> 更新任务进度”完整链路。
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AiTaskRepository aiTaskRepository;
  private final DocumentRepository documentRepository;
  private final SectionRepository sectionRepository;
  private final SectionVersionRepository sectionVersionRepository;
  private final KnowledgeBaseService knowledgeBaseService;
  private final SectionChunkRefService sectionChunkRefService;
  private final AiClient aiClient;
  private final ExecutorService aiDocumentExecutor;

  public DocumentAutoWriteService(AiTaskRepository aiTaskRepository,
      DocumentRepository documentRepository,
      SectionRepository sectionRepository,
      SectionVersionRepository sectionVersionRepository,
      KnowledgeBaseService knowledgeBaseService,
      SectionChunkRefService sectionChunkRefService,
      AiClient aiClient,
      @Qualifier("aiDocumentExecutor") ExecutorService aiDocumentExecutor) {
    this.aiTaskRepository = aiTaskRepository;
    this.documentRepository = documentRepository;
    this.sectionRepository = sectionRepository;
    this.sectionVersionRepository = sectionVersionRepository;
    this.knowledgeBaseService = knowledgeBaseService;
    this.sectionChunkRefService = sectionChunkRefService;
    this.aiClient = aiClient;
    this.aiDocumentExecutor = aiDocumentExecutor;
  }

  @Transactional
  public AiTaskResponse autoWriteDocument(AiDocumentAutoWriteRequest request, Long operatorId) {
    // [AI-READ] 入口方法：仅创建任务并投递异步执行，不在请求线程里做耗时生成。
    Document document = documentRepository.findById(request.getDocumentId()).orElseThrow(EntityNotFoundException::new);
    AiTask task = AiTask.builder()
        .sectionId(null)
        .sourceVersionId(null)
        .prompt(buildAutoWritePromptPayload(request))
        .status(AiTaskStatus.RUNNING)
        .startedAt(Instant.now())
        .createdBy(operatorId)
        .response("{\"status\":\"RUNNING\",\"total\":0,\"success\":0,\"failed\":0}")
        .build();
    aiTaskRepository.save(task);
    aiDocumentExecutor.submit(() -> runAutoWrite(task.getId(), document.getId(), request, operatorId));
    return toResponse(task);
  }

  @Transactional
  public void runAutoWrite(Long taskId, Long documentId, AiDocumentAutoWriteRequest request, Long operatorId) {
    // [AI-READ] 异步主流程：按章节顺序执行生成；每处理一个章节就刷新一次任务进度，便于前端轮询展示。
    AiTask task = aiTaskRepository.findById(taskId).orElseThrow(EntityNotFoundException::new);
    try {
      List<Section> sections = sectionRepository.findTreeByDocumentId(documentId);
      List<Section> orderedSections = orderSectionsForAutoWrite(sections);
      int total = orderedSections.size();
      int success = 0;
      int failed = 0;
      List<Map<String, Object>> detail = new ArrayList<>();

      for (Section section : orderedSections) {
        try {
          // [AI-READ] supplement 使用当前章节已有正文（可为空），用于给模型提供最小必要上下文。
          String sectionPath = buildSectionPath(section, sections);
          String supplement = section.getCurrentVersion() == null ? "" : safe(section.getCurrentVersion().getContent());
          DraftOutput draft = generateSectionDraft(section, sectionPath, supplement, request, operatorId);
          persistSectionDraft(section, draft.content(), draft.hits(), operatorId, request.isOverwriteExisting(),
              taskId);
          success++;
          detail.add(buildStep(section.getId(), section.getTitle(), "SUCCESS", null));
        } catch (Exception ex) {
          failed++;
          detail.add(buildStep(section.getId(), section.getTitle(), "FAILED", ex.getMessage()));
        }
        // [AI-READ] 增量更新任务响应，避免长任务期间“看起来卡死”。
        task.setResponse(buildAutoWriteProgressPayload(total, success, failed, detail, "RUNNING"));
        aiTaskRepository.save(task);
      }

      if (failed > 0) {
        task.setStatus(AiTaskStatus.FAILED);
        task.setErrorMessage("部分章节生成失败，成功 " + success + " / 总计 " + total);
      } else {
        task.setStatus(AiTaskStatus.SUCCESS);
      }
      task.setResponse(buildAutoWriteProgressPayload(total, success, failed, detail, task.getStatus().name()));
      task.setFinishedAt(Instant.now());
      aiTaskRepository.save(task);
    } catch (Exception ex) {
      task.setStatus(AiTaskStatus.FAILED);
      task.setErrorMessage(ex.getMessage());
      task.setFinishedAt(Instant.now());
      task.setResponse(buildAutoWriteProgressPayload(0, 0, 0, List.of(), "FAILED"));
      aiTaskRepository.save(task);
    }
  }

  private List<Section> orderSectionsForAutoWrite(List<Section> sections) {
    // [AI-READ] 先构造父子索引再深度优先遍历，保证生成顺序稳定（同层按 sortIndex）。
    Map<Long, List<Section>> children = new HashMap<>();
    List<Section> roots = new ArrayList<>();
    for (Section section : sections) {
      if (section.getParent() == null) {
        roots.add(section);
      } else {
        children.computeIfAbsent(section.getParent().getId(), k -> new ArrayList<>()).add(section);
      }
    }
    roots.sort(Comparator.comparing(Section::getSortIndex));
    children.values().forEach(list -> list.sort(Comparator.comparing(Section::getSortIndex)));
    List<Section> ordered = new ArrayList<>();
    for (Section root : roots) {
      traverseSection(root, children, ordered);
    }
    return ordered;
  }

  private void traverseSection(Section current, Map<Long, List<Section>> children, List<Section> ordered) {
    ordered.add(current);
    for (Section child : children.getOrDefault(current.getId(), List.of())) {
      traverseSection(child, children, ordered);
    }
  }

  private String buildSectionPath(Section section, List<Section> allSections) {
    // [AI-READ] 生成“根 > 子 > 当前”的完整路径，供检索 query 和章节意图对齐使用。
    Map<Long, Section> byId = allSections.stream().collect(Collectors.toMap(Section::getId, s -> s, (a, b) -> a));
    List<String> chain = new ArrayList<>();
    Section cursor = section;
    while (cursor != null) {
      chain.add(cursor.getTitle());
      cursor = cursor.getParent() == null ? null : byId.get(cursor.getParent().getId());
    }
    java.util.Collections.reverse(chain);
    return String.join(" > ", chain);
  }

  private DraftOutput generateSectionDraft(Section section,
      String sectionPath,
      String supplement,
      AiDocumentAutoWriteRequest request,
      Long operatorId) {
    // [AI-READ] 先检索知识片段，再把片段拼成 LLM 的上下文输入。
    List<KnowledgeSearchResult> hits = List.of();
    String retrievalContext = "";
    if (request.getKnowledgeBaseId() != null) {
      KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
      // [AI-READ] 按你的要求：只使用“章节标题”检索，不再拼接路径和补充信息。
      String query = safe(section.getTitle());
      searchRequest.setQuery(query);
      searchRequest.setTopK(8);
      searchRequest.setCandidateTopK(16);
      searchRequest.setMinScore(0.0);
      searchRequest.setRerank(true);
      hits = knowledgeBaseService.searchAsUser(
          request.getKnowledgeBaseId(), searchRequest, operatorId);
      // [AI-READ] 仍保留“完整可追溯”的原则，但为避免上下文污染，仅传入前 8 条重排结果。
      retrievalContext = hits.stream().limit(8)
          .map(hit -> "- [chunk#" + hit.getChunkId() + "] " + normalizeHitForPrompt(hit.getContent()))
          .collect(Collectors.joining("\n"));
    }

    String systemPrompt = """
        你是售前文档写作助手。
        必须围绕“章节标题”写作，保持专业、客观、结构清晰。
        如果提供了知识片段，只能使用与“当前章节标题”直接相关的片段；不相关片段必须忽略。
        对父级概述章节，禁止把下级章节的细节（实现步骤、伪代码、配置项）整段搬入。
        不要扩展章节结构，不要输出“1.1/###”这类子章节标题，不要跨章节拼接无关主题。
        不要在正文中输出 [chunk#123] 这类引用标记。

        你必须输出 JSON 对象，结构如下：
        {
          "html": "<p>...</p>",
          "usedChunkIds": [1306,1304]
        }
        要求：
        1) html 只放正文 HTML 片段，不要 markdown，不要 ``` 包裹。
        2) 可用标签：p,strong,em,u,span,br,ul,ol,li,table,thead,tbody,tr,th,td,blockquote,h3,h4,img。
        3) usedChunkIds 只能填写知识片段里真实出现过的 chunkId。
        4) 若知识片段包含图片标记 [img ...]URL，需要在 html 中转成 <img src="URL" alt="图片说明" />。
        5) 优先选择最相关片段写作，不要“把全部片段拼接”。
        6) 默认使用中文输出，除非术语必须保留英文。
        """;
    String userPrompt = """
        【章节路径】
        %s

        【章节标题】
        %s

        【项目参数】
        %s

        【补充信息】
        %s

        【知识片段】
        %s

        【输出示例风格（仅风格参考，不要照抄内容）】
        <p><span style="color: rgb(225, 60, 57);"><u><em><strong>智能决策层</strong></em></u></span><strong> = 在“检索”和“生成”之间插入一个判断大脑。<br></strong><img src="/files/images/xxx.png" alt="示意图"/></p>
        """
        .formatted(
            sectionPath,
            section.getTitle(),
            safe(request.getProjectParams()),
            supplement.isBlank() ? "(无)" : supplement,
            retrievalContext.isBlank() ? "(无)" : retrievalContext);
    String raw = aiClient.chat(systemPrompt, userPrompt);
    GeneratedPayload payload = parseGeneratedPayload(raw, hits);
    List<KnowledgeSearchResult> citedHits = resolveCitedHits(payload.usedChunkIds(), hits);
    return new DraftOutput(payload.html(), citedHits.isEmpty() ? hits : citedHits);
  }

  private void persistSectionDraft(Section section,
      String generated,
      List<KnowledgeSearchResult> hits,
      Long operatorId,
      boolean overwriteExisting,
      Long taskId) {
    // [AI-READ] 统一落库入口：创建或覆盖 currentVersion，并同步更新 section_chunk_ref。
    String content = safe(generated);
    if (content.isBlank()) {
      throw new IllegalStateException("AI 返回空内容");
    }
    SectionVersion current = section.getCurrentVersion();
    if (current == null) {
      // [AI-READ] 首次生成：创建 currentVersion 并写入引用。
      current = SectionVersion.builder()
          .section(section)
          .content(content)
          .summary("AI自动编写")
          .sourceType(SectionSourceType.AI)
          .sourceRef("ai-auto-task-" + taskId)
          .createdBy(operatorId)
          .build();
      sectionVersionRepository.save(current);
      section.setCurrentVersion(current);
      sectionRepository.save(section);
      sectionChunkRefService.replaceByHits(section, current, hits,
          operatorId);
      return;
    }
    // [AI-READ] 非覆盖模式下，已有内容不改写，避免误覆盖人工编辑结果。
    if (!overwriteExisting && current.getContent() != null && !current.getContent().isBlank()) {
      return;
    }
    current.setContent(content);
    current.setSummary("AI自动编写");
    current.setSourceType(SectionSourceType.AI);
    current.setSourceRef("ai-auto-task-" + taskId);
    current.setCreatedBy(operatorId);
    sectionVersionRepository.save(current);
    sectionChunkRefService.replaceByHits(section, current, hits,
        operatorId);
  }

  private record DraftOutput(String content, List<KnowledgeSearchResult> hits) {
  }

  private record GeneratedPayload(String html, List<Long> usedChunkIds) {
  }

  private String normalizeHitForPrompt(String content) {
    // [AI-READ] Prompt 控制：结构化表格 JSON 只保留提示语，避免 token 被大 JSON 吞噬。
    String text = safe(content);
    if (text.contains("[TABLE_JSON]")) {
      int idx = text.indexOf("[TABLE_JSON]");
      String head = text.substring(0, idx).trim();
      return (head.isBlank() ? "结构化表格数据" : head) + "（表格JSON已省略）";
    }
    return text;
  }

  // [AI-READ] 解析模型输出：
  // 1) 优先按 JSON 提取 html + usedChunkIds；
  // 2) 失败时回退为纯文本 HTML，并默认引用前几条命中。
  private GeneratedPayload parseGeneratedPayload(String rawResponse, List<KnowledgeSearchResult> hits) {
    String cleaned = stripMarkdownFence(rawResponse);
    try {
      JsonNode root = MAPPER.readTree(cleaned);
      String html = safe(root.path("html").asText(""));
      if (html.isBlank()) {
        html = safe(root.path("content").asText(""));
      }
      List<Long> used = new ArrayList<>();
      JsonNode usedNode = root.path("usedChunkIds");
      if (usedNode.isArray()) {
        for (JsonNode node : usedNode) {
          if (node.isNumber()) {
            used.add(node.asLong());
          } else if (node.isTextual()) {
            try {
              used.add(Long.parseLong(node.asText().trim()));
            } catch (NumberFormatException ignored) {
            }
          }
        }
      }
      if (html.isBlank()) {
        html = cleaned;
      }
      return new GeneratedPayload(html, used);
    } catch (Exception ignored) {
      return new GeneratedPayload(cleaned, defaultChunkIds(hits, 6));
    }
  }

  private String stripMarkdownFence(String text) {
    String raw = safe(text);
    if (raw.startsWith("```")) {
      int firstLine = raw.indexOf('\n');
      int endFence = raw.lastIndexOf("```");
      if (firstLine > 0 && endFence > firstLine) {
        return raw.substring(firstLine + 1, endFence).trim();
      }
    }
    return raw;
  }

  private List<Long> defaultChunkIds(List<KnowledgeSearchResult> hits, int limit) {
    if (hits == null || hits.isEmpty()) {
      return List.of();
    }
    return hits.stream()
        .map(KnowledgeSearchResult::getChunkId)
        .filter(id -> id != null)
        .distinct()
        .limit(limit)
        .toList();
  }

  private List<KnowledgeSearchResult> resolveCitedHits(List<Long> usedChunkIds, List<KnowledgeSearchResult> hits) {
    if (hits == null || hits.isEmpty()) {
      return List.of();
    }
    if (usedChunkIds == null || usedChunkIds.isEmpty()) {
      return hits;
    }
    Map<Long, KnowledgeSearchResult> byId = hits.stream()
        .filter(h -> h.getChunkId() != null)
        .collect(Collectors.toMap(KnowledgeSearchResult::getChunkId, h -> h, (a, b) -> a, LinkedHashMap::new));
    Set<Long> allowed = byId.keySet();
    List<KnowledgeSearchResult> refs = new ArrayList<>();
    for (Long id : usedChunkIds) {
      if (id == null || !allowed.contains(id)) {
        continue;
      }
      refs.add(byId.get(id));
      if (refs.size() >= 12) {
        break;
      }
    }
    return refs.isEmpty() ? hits : refs;
  }

  private Map<String, Object> buildStep(Long sectionId, String title, String status, String error) {
    Map<String, Object> step = new LinkedHashMap<>();
    step.put("sectionId", sectionId);
    step.put("title", title);
    step.put("status", status);
    if (error != null && !error.isBlank()) {
      step.put("error", error.length() > 300 ? error.substring(0, 300) : error);
    }
    return step;
  }

  private String buildAutoWriteProgressPayload(int total,
      int success,
      int failed,
      List<Map<String, Object>> detail,
      String status) {
    // [AI-READ] 任务进度序列化为 JSON 字符串，前端直接读取展示。
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", status);
    payload.put("total", total);
    payload.put("success", success);
    payload.put("failed", failed);
    payload.put("steps", detail);
    try {
      return MAPPER.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      return "{\"status\":\"" + status + "\",\"total\":" + total + ",\"success\":" + success + ",\"failed\":" + failed
          + "}";
    }
  }

  private String buildAutoWritePromptPayload(AiDocumentAutoWriteRequest request) {
    // [AI-READ] 将任务入参快照保存到 AiTask.prompt，便于排障和任务追踪。
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("documentId", request.getDocumentId());
    payload.put("knowledgeBaseId", request.getKnowledgeBaseId());
    payload.put("overwriteExisting", request.isOverwriteExisting());
    payload.put("projectParams", safe(request.getProjectParams()));
    try {
      return MAPPER.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  private String safe(String text) {
    return text == null ? "" : text.trim();
  }

  private AiTaskResponse toResponse(AiTask task) {
    return AiTaskResponse.builder()
        .id(task.getId())
        .sectionId(task.getSectionId())
        .sourceVersionId(task.getSourceVersionId())
        .resultVersionId(task.getResultVersionId())
        .status(task.getStatus())
        .errorMessage(task.getErrorMessage())
        .response(task.getResponse())
        .createdAt(task.getCreatedAt())
        .build();
  }
}
