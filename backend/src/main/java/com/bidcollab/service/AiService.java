package com.bidcollab.service;

import com.bidcollab.ai.AiClient;
import com.bidcollab.dto.AiAssistantAskRequest;
import com.bidcollab.dto.AiAssistantAskResponse;
import com.bidcollab.dto.AiAssistantCitation;
import com.bidcollab.dto.AiDocumentAutoWriteRequest;
import com.bidcollab.dto.AiRewriteRequest;
import com.bidcollab.dto.KnowledgeSearchRequest;
import com.bidcollab.dto.KnowledgeSearchResult;
import com.bidcollab.dto.AiTaskResponse;
import com.bidcollab.dto.SectionVersionCreateRequest;
import com.bidcollab.entity.AiTask;
import com.bidcollab.entity.Document;
import com.bidcollab.entity.KnowledgeBase;
import com.bidcollab.entity.KnowledgeDocument;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionVersion;
import com.bidcollab.enums.AiTaskStatus;
import com.bidcollab.enums.SectionSourceType;
import com.bidcollab.repository.AiTaskRepository;
import com.bidcollab.repository.DocumentRepository;
import com.bidcollab.repository.KnowledgeBaseRepository;
import com.bidcollab.repository.KnowledgeDocumentRepository;
import com.bidcollab.repository.SectionRepository;
import com.bidcollab.repository.SectionVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final AiTaskRepository aiTaskRepository;
  private final DocumentRepository documentRepository;
  private final SectionRepository sectionRepository;
  private final SectionVersionRepository sectionVersionRepository;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final KnowledgeDocumentRepository knowledgeDocumentRepository;
  private final KnowledgeBaseService knowledgeBaseService;
  private final SectionChunkRefService sectionChunkRefService;
  private final AiClient aiClient;
  private final CurrentUserService currentUserService;
  private final ExecutorService aiDocumentExecutor;

  public AiService(AiTaskRepository aiTaskRepository,
      DocumentRepository documentRepository,
      SectionRepository sectionRepository,
      SectionVersionRepository sectionVersionRepository,
      KnowledgeBaseRepository knowledgeBaseRepository,
      KnowledgeDocumentRepository knowledgeDocumentRepository,
      KnowledgeBaseService knowledgeBaseService,
      SectionChunkRefService sectionChunkRefService,
      AiClient aiClient,
      CurrentUserService currentUserService,
      @Qualifier("aiDocumentExecutor") ExecutorService aiDocumentExecutor) {
    this.aiTaskRepository = aiTaskRepository;
    this.documentRepository = documentRepository;
    this.sectionRepository = sectionRepository;
    this.sectionVersionRepository = sectionVersionRepository;
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    this.knowledgeBaseService = knowledgeBaseService;
    this.sectionChunkRefService = sectionChunkRefService;
    this.aiClient = aiClient;
    this.currentUserService = currentUserService;
    this.aiDocumentExecutor = aiDocumentExecutor;
  }

  @Transactional
  public AiTaskResponse rewrite(AiRewriteRequest request) {
    Section section = sectionRepository.findById(request.getSectionId()).orElseThrow(EntityNotFoundException::new);
    SectionVersion sourceVersion = sectionVersionRepository.findById(request.getSourceVersionId())
        .orElseThrow(EntityNotFoundException::new);

    AiTask task = AiTask.builder()
        .sectionId(section.getId())
        .sourceVersionId(sourceVersion.getId())
        .prompt(request.getProjectParams())
        .status(AiTaskStatus.RUNNING)
        .startedAt(Instant.now())
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    aiTaskRepository.save(task);

    try {
      String systemPrompt = "你是售前文档助手，基于输入内容和项目参数改写美化，不得编造事实。";
      String userPrompt = "【项目参数】\n" + request.getProjectParams() + "\n\n【原章节】\n" + sourceVersion.getContent()
          + "\n\n请输出改写后的章节正文。";
      String output = aiClient.chat(systemPrompt, userPrompt);
      SectionVersionCreateRequest versionRequest = new SectionVersionCreateRequest();
      versionRequest.setContent(output);
      versionRequest.setSummary("AI改写");
      SectionVersion created = SectionVersion.builder()
          .section(section)
          .content(versionRequest.getContent())
          .summary(versionRequest.getSummary())
          .sourceType(SectionSourceType.AI)
          .sourceRef("ai-task-" + task.getId())
          .createdBy(currentUserService.getCurrentUserId())
          .build();
      sectionVersionRepository.save(created);
      section.setCurrentVersion(created);

      task.setStatus(AiTaskStatus.SUCCESS);
      task.setResultVersionId(created.getId());
      task.setResponse(output);
      task.setFinishedAt(Instant.now());
    } catch (Exception ex) {
      task.setStatus(AiTaskStatus.FAILED);
      task.setErrorMessage(ex.getMessage());
      task.setFinishedAt(Instant.now());
    }

    return toResponse(task);
  }

  @Transactional
  public AiTaskResponse autoWriteDocument(AiDocumentAutoWriteRequest request) {
    Document document = documentRepository.findById(request.getDocumentId()).orElseThrow(EntityNotFoundException::new);
    Long operatorId = currentUserService.getCurrentUserId();
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
  protected void runAutoWrite(Long taskId, Long documentId, AiDocumentAutoWriteRequest request, Long operatorId) {
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

  public AiTaskResponse getTask(Long taskId) {
    AiTask task = aiTaskRepository.findById(taskId).orElseThrow(EntityNotFoundException::new);
    return toResponse(task);
  }

  /**
   * RAG（检索增强生成）问答入口。
   * 整体流程：解析目标知识库 → 多库向量检索 → 合并排序 → 构建上下文 → 调用 LLM 生成回答 → 组装引用列表。
   *
   * @param request 包含用户问题、知识库ID（可选）、检索参数等
   * @return 包含 AI 回答、匹配片段数、引用列表的响应对象
   */
  // [AI-READ] 检索增强问答：先检索知识片段，再让模型仅基于片段回答。
  public AiAssistantAskResponse ask(AiAssistantAskRequest request) {

    // ──────────────────────────────────────────
    // 第一步：解析目标知识库
    // 如果请求指定了 knowledgeBaseId，则只查该库；否则检索全部知识库
    // ──────────────────────────────────────────
    List<KnowledgeBase> targetBases = resolveTargetKnowledgeBases(request.getKnowledgeBaseId());
    if (targetBases.isEmpty()) {
      // 无可用知识库时直接返回提示，避免后续无意义的检索
      return AiAssistantAskResponse.builder()
          .answer("当前没有可用的知识库，请先创建并导入知识文档。")
          .matchedChunkCount(0)
          .citations(List.of())
          .build();
    }

    // ──────────────────────────────────────────
    // 第二步：遍历所有目标知识库，逐库执行向量检索
    // allHits - 汇总所有库的检索结果
    // chunkToKb - 记录每个 chunk 来源于哪个知识库，后续构建引用时使用
    // ──────────────────────────────────────────
    List<KnowledgeSearchResult> allHits = new ArrayList<>();
    Map<Long, Long> chunkToKb = new HashMap<>();
    for (KnowledgeBase kb : targetBases) {
      // 构造检索请求，设置各项召回参数
      KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
      searchRequest.setQuery(request.getQuery());
      // topK: 最终返回的片段数，最低保底 4 条，默认 8 条
      searchRequest.setTopK(Math.max(4, request.getTopK() == null ? 8 : request.getTopK()));
      // candidateTopK: 粗召回候选数（topK 的 3 倍），用于 rerank 前的预筛选
      searchRequest.setCandidateTopK(Math.max(12, searchRequest.getTopK() * 3));
      // minScore: 最低相似度阈值，低于此分数的片段会被过滤掉
      searchRequest.setMinScore(request.getMinScore() == null ? 0.05 : request.getMinScore());
      // 是否启用 rerank（二次精排），由前端控制
      searchRequest.setRerank(Boolean.TRUE.equals(request.getRerank()));

      // 执行向量检索，并将结果汇总
      List<KnowledgeSearchResult> results = knowledgeBaseService.search(kb.getId(), searchRequest);
      // 兜底重试：高阈值可能导致相关片段被过滤掉，降阈值再召回一次
      boolean shouldRetry = shouldRetryWithLowerThreshold(request, searchRequest);
      if (results.isEmpty() && shouldRetry) {
        searchRequest.setMinScore(0.0);
        searchRequest.setRerank(false);
        searchRequest.setCandidateTopK(Math.max(searchRequest.getCandidateTopK(), 36));
        results = knowledgeBaseService.search(kb.getId(), searchRequest);
      }
      for (KnowledgeSearchResult item : results) {
        allHits.add(item);
        // 建立 chunkId → knowledgeBaseId 的映射关系
        chunkToKb.put(item.getChunkId(), kb.getId());
      }
    }

    // ──────────────────────────────────────────
    // 第三步：合并排序 & 截取上下文片段
    // 按相似度分数降序排列，取前 10 条作为 LLM 的上下文输入
    // ──────────────────────────────────────────
    allHits.sort(Comparator.comparingDouble(KnowledgeSearchResult::getScore).reversed());
    int contextTopK = Math.min(10, allHits.size());
    List<KnowledgeSearchResult> contextHits = allHits.subList(0, contextTopK);

    // 如果检索结果为空，返回友好提示
    if (contextHits.isEmpty()) {
      return AiAssistantAskResponse.builder()
          .answer("未检索到足够相关的知识片段，请调整问题关键词后重试。")
          .matchedChunkCount(0)
          .citations(List.of())
          .build();
    }

    // ──────────────────────────────────────────
    // 第四步：批量查询文档元数据
    // 通过 contextHits 中的 documentId 去重后批量加载文档信息，
    // 用于后续在引用列表中展示文档标题等
    // ──────────────────────────────────────────
    Map<Long, KnowledgeDocument> docById = new HashMap<>();
    for (KnowledgeDocument doc : knowledgeDocumentRepository.findAllById(
        contextHits.stream().map(KnowledgeSearchResult::getDocumentId).distinct().toList())) {
      docById.put(doc.getId(), doc);
    }
    // 知识库 id → 知识库对象的映射，用于引用中展示知识库名称
    Map<Long, KnowledgeBase> kbById = new HashMap<>();
    for (KnowledgeBase kb : targetBases) {
      kbById.put(kb.getId(), kb);
    }

    // ──────────────────────────────────────────
    // 第五步：构建上下文文本 & 调用 LLM 生成回答
    // 将检索到的知识片段拼接为结构化文本，连同用户问题一起发送给大模型
    // ──────────────────────────────────────────
    String context = buildContext(contextHits, chunkToKb, kbById, docById);
    String answer;
    try {
      // 系统提示词：约束模型只能基于知识片段回答，不得编造
      String systemPrompt = """
          你是售前招标文档智能助手。你需要基于"知识片段"回答。
          要求：
          1) 不得编造片段中不存在的事实；
          2) 如果信息不足，明确说"知识库暂无充分依据";
          3) 回答尽量结构化、简洁，并给出可执行建议；
          4) 使用中文。
          5) 可以参考用户提供的数据;
          """;
      // 用户提示词：包含原始问题和检索到的知识片段
      String userPrompt = "【用户问题】\n" + request.getQuery() + "\n\n【知识片段】\n" + context;
      answer = aiClient.chat(systemPrompt, userPrompt);
    } catch (Exception ex) {
      // LLM 调用异常时返回兜底文案，不影响整体流程
      answer = "AI回答失败，请稍后重试。";
    }

    // ──────────────────────────────────────────
    // 第六步：组装引用（Citation）列表
    // 取相似度最高的前 8 条片段，附带知识库名称、文档标题、片段摘要等信息，
    // 方便前端展示回答的来源依据
    // ──────────────────────────────────────────
    List<AiAssistantCitation> citations = contextHits.stream().limit(8).map(hit -> {
      Long kbId = chunkToKb.get(hit.getChunkId());
      KnowledgeBase kb = kbById.get(kbId);
      KnowledgeDocument doc = docById.get(hit.getDocumentId());
      return AiAssistantCitation.builder()
          .knowledgeBaseId(kbId)
          .knowledgeBaseName(kb == null ? "-" : kb.getName())
          .documentId(hit.getDocumentId())
          .documentTitle(doc == null ? "未知文档" : doc.getTitle())
          .chunkId(hit.getChunkId())
          .score(hit.getScore())
          // 截取片段内容前 220 字符作为摘要预览
          .snippet(snippet(hit.getContent(), 220))
          .build();
    }).toList();

    // 返回最终响应：AI 回答 + 总匹配数 + 引用列表
    return AiAssistantAskResponse.builder()
        .answer(answer)
        .matchedChunkCount(allHits.size())
        .citations(citations)
        .build();
  }

  private List<KnowledgeBase> resolveTargetKnowledgeBases(Long knowledgeBaseId) {
    if (knowledgeBaseId != null) {
      return knowledgeBaseRepository.findById(knowledgeBaseId).map(List::of).orElse(List.of());
    }
    return knowledgeBaseRepository.findAll();
  }

  private String buildContext(List<KnowledgeSearchResult> hits,
      Map<Long, Long> chunkToKb,
      Map<Long, KnowledgeBase> kbById,
      Map<Long, KnowledgeDocument> docById) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < hits.size(); i++) {
      KnowledgeSearchResult hit = hits.get(i);
      Long kbId = chunkToKb.get(hit.getChunkId());
      KnowledgeBase kb = kbById.get(kbId);
      KnowledgeDocument doc = docById.get(hit.getDocumentId());
      sb.append("[").append(i + 1).append("]")
          .append(" KB=").append(kb == null ? "-" : kb.getName())
          .append(" | DOC=").append(doc == null ? "未知文档" : doc.getTitle())
          .append(" | SCORE=").append(String.format("%.3f", hit.getScore()))
          .append("\n")
          .append(hit.getContent())
          .append("\n\n");
    }
    return sb.toString();
  }

  private String snippet(String content, int maxLen) {
    if (content == null || content.isBlank()) {
      return "";
    }
    String normalized = content.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= maxLen) {
      return normalized;
    }
    return normalized.substring(0, maxLen) + "...";
  }

  /**
   * 仅对“信息量足够、确有召回价值”的查询触发二次重试，避免大库场景每次都双重检索。
   */
  private boolean shouldRetryWithLowerThreshold(AiAssistantAskRequest request, KnowledgeSearchRequest searchRequest) {
    if (searchRequest.getMinScore() == null || searchRequest.getMinScore() <= 0.0) {
      return false;
    }
    String query = request.getQuery() == null ? "" : request.getQuery().trim();
    int effectiveLen = query.replaceAll("\\s+", "").length();
    int topK = request.getTopK() == null ? 8 : request.getTopK();
    // 查询较长或期望召回较多时再重试
    return effectiveLen >= 8 || topK >= 6;
  }

  private List<Section> orderSectionsForAutoWrite(List<Section> sections) {
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
    List<KnowledgeSearchResult> hits = List.of();
    String retrievalContext = "";
    if (request.getKnowledgeBaseId() != null) {
      KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
      String query = sectionPath + (supplement.isBlank() ? "" : ("\n" + supplement));
      searchRequest.setQuery(query);
      searchRequest.setTopK(4);
      searchRequest.setCandidateTopK(16);
      searchRequest.setMinScore(0.12);
      searchRequest.setRerank(true);
      hits = knowledgeBaseService.searchAsUser(
          request.getKnowledgeBaseId(), searchRequest, operatorId);
      hits = filterHitsBySection(sectionPath, section.getTitle(), hits);
      retrievalContext = hits.stream().limit(6)
          .map(hit -> "- [chunk#" + hit.getChunkId() + "] " + normalizeHitForPrompt(hit.getContent()))
          .collect(Collectors.joining("\n"));
    }

    String systemPrompt = """
        你是售前文档写作助手。
        必须围绕“章节标题”写作，保持专业、客观、结构清晰。
        如果提供了知识片段，只能基于片段和补充信息写，不得编造。
        不要扩展章节结构，不要输出“1.1/###”这类子章节标题，不要跨章节拼接无关主题。
        不要在正文中输出 [chunk#123] 这类引用标记。
        默认使用中文输出，除非术语必须保留英文。

        输出要求（必须遵守）：
        1) 只输出“正文 HTML 片段”，不要输出 markdown，不要输出 ``` 代码块，不要输出解释性前言。
        2) 可用标签：p,strong,em,u,span,br,ul,ol,li,table,thead,tbody,tr,th,td,blockquote,h3,h4,img。
        3) 需要强调时可使用内联样式（如 span 的 color）。
        4) 若知识片段包含图片标记 [img ...]URL，需要在正文中转成 <img src="URL" alt="图片说明" />。
        5) 输出必须可直接写入富文本编辑器。
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
    return new DraftOutput(aiClient.chat(systemPrompt, userPrompt), hits);
  }

  private void persistSectionDraft(Section section,
      String generated,
      List<KnowledgeSearchResult> hits,
      Long operatorId,
      boolean overwriteExisting,
      Long taskId) {
    String content = safe(generated);
    if (content.isBlank()) {
      throw new IllegalStateException("AI 返回空内容");
    }
    SectionVersion current = section.getCurrentVersion();
    if (current == null) {
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
      sectionChunkRefService.replaceByHits(section, current, hits, operatorId);
      return;
    }
    if (!overwriteExisting && current.getContent() != null && !current.getContent().isBlank()) {
      return;
    }
    current.setContent(content);
    current.setSummary("AI自动编写");
    current.setSourceType(SectionSourceType.AI);
    current.setSourceRef("ai-auto-task-" + taskId);
    current.setCreatedBy(operatorId);
    sectionVersionRepository.save(current);
    sectionChunkRefService.replaceByHits(section, current, hits, operatorId);
  }

  private record DraftOutput(String content, List<KnowledgeSearchResult> hits) {
  }

  private List<KnowledgeSearchResult> filterHitsBySection(String sectionPath, String sectionTitle,
      List<KnowledgeSearchResult> hits) {
    if (hits == null || hits.isEmpty()) {
      return List.of();
    }
    Set<String> focusTerms = buildFocusTerms(sectionPath, sectionTitle);
    if (focusTerms.isEmpty()) {
      return hits.stream().limit(6).toList();
    }

    class Scored {
      private final KnowledgeSearchResult hit;
      private final int match;
      private final double score;

      private Scored(KnowledgeSearchResult hit, int match, double score) {
        this.hit = hit;
        this.match = match;
        this.score = score;
      }
    }

    List<Scored> scored = hits.stream().map(hit -> {
      String txt = safe(hit.getContent());
      int match = 0;
      for (String term : focusTerms) {
        if (txt.contains(term)) {
          match++;
        }
      }
      double score = hit.getScore() + (match * 0.08);
      return new Scored(hit, match, score);
    }).sorted((a, b) -> Double.compare(b.score, a.score)).toList();

    List<KnowledgeSearchResult> strong = scored.stream()
        .filter(s -> s.match > 0)
        .limit(6)
        .map(s -> s.hit)
        .toList();
    if (!strong.isEmpty()) {
      return strong;
    }
    return scored.stream().limit(4).map(s -> s.hit).toList();
  }

  private Set<String> buildFocusTerms(String sectionPath, String sectionTitle) {
    Set<String> terms = new LinkedHashSet<>();
    for (String part : (safe(sectionPath) + " " + safe(sectionTitle)).split("[>\\s、，,：:；;（）()\\-_/]+")) {
      String term = safe(part);
      if (term.length() >= 2 && !Set.of("章节", "内容", "说明", "部分", "项目", "方案", "要求").contains(term)) {
        terms.add(term);
      }
    }
    return terms;
  }

  private String normalizeHitForPrompt(String content) {
    String text = safe(content);
    if (text.contains("[TABLE_JSON]")) {
      int idx = text.indexOf("[TABLE_JSON]");
      String head = text.substring(0, idx).trim();
      return (head.isBlank() ? "结构化表格数据" : head) + "（表格JSON已省略）";
    }
    return text;
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
