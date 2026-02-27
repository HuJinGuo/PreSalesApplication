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
import com.bidcollab.entity.KnowledgeBase;
import com.bidcollab.entity.KnowledgeDocument;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionChunkRef;
import com.bidcollab.entity.SectionVersion;
import com.bidcollab.enums.AiTaskStatus;
import com.bidcollab.enums.SectionSourceType;
import com.bidcollab.repository.AiTaskRepository;
import com.bidcollab.repository.KnowledgeBaseRepository;
import com.bidcollab.repository.KnowledgeDocumentRepository;
import com.bidcollab.repository.SectionRepository;
import com.bidcollab.repository.SectionChunkRefRepository;
import com.bidcollab.repository.SectionVersionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {
  private final AiTaskRepository aiTaskRepository;
  private final SectionRepository sectionRepository;
  private final SectionVersionRepository sectionVersionRepository;
  private final SectionChunkRefRepository sectionChunkRefRepository;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final KnowledgeDocumentRepository knowledgeDocumentRepository;
  private final KnowledgeBaseService knowledgeBaseService;
  private final DocumentAutoWriteService documentAutoWriteService;
  private final AiClient aiClient;
  private final CurrentUserService currentUserService;

  public AiService(AiTaskRepository aiTaskRepository,
      SectionRepository sectionRepository,
      SectionVersionRepository sectionVersionRepository,
      SectionChunkRefRepository sectionChunkRefRepository,
      KnowledgeBaseRepository knowledgeBaseRepository,
      KnowledgeDocumentRepository knowledgeDocumentRepository,
      KnowledgeBaseService knowledgeBaseService,
      DocumentAutoWriteService documentAutoWriteService,
      AiClient aiClient,
      CurrentUserService currentUserService) {
    this.aiTaskRepository = aiTaskRepository;
    this.sectionRepository = sectionRepository;
    this.sectionVersionRepository = sectionVersionRepository;
    this.sectionChunkRefRepository = sectionChunkRefRepository;
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    this.knowledgeBaseService = knowledgeBaseService;
    this.documentAutoWriteService = documentAutoWriteService;
    this.aiClient = aiClient;
    this.currentUserService = currentUserService;
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
      List<SectionChunkRef> refs = sectionChunkRefRepository
          .findBySectionIdOrderByParagraphIndexAscIdAsc(section.getId());
      String citationContext = buildCitationContext(refs);
      boolean hasCitations = !citationContext.isBlank();
      String systemPrompt = """
          你是售前文档助手。
          请仅围绕“当前章节标题”写作，并优先使用“引用片段”中的信息。
          要求：
          1) 使用中文输出；
          2) 保持专业、准确、简洁，不要编造事实；
          3) 不要写跨章节内容，不要新增子章节标题；
          4) 若引用片段不足，仅做最小必要补全，明确表述为“基于现有信息”。
          5) 输出为可直接落库的 HTML 片段（允许 p,strong,em,u,span,br,ul,ol,li,table,thead,tbody,tr,th,td,blockquote）。
          """;
      String userPrompt = "【章节标题】\n" + section.getTitle()
          + "\n\n【项目参数】\n" + request.getProjectParams()
          + "\n\n【引用片段】\n" + (hasCitations ? citationContext : "(当前章节暂无引用片段)")
          + "\n\n【原章节（仅兜底参考）】\n" + sourceVersion.getContent()
          + "\n\n请输出改写后的章节正文（HTML）。";
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

    return AiTaskResponse.from(task);
  }

  private String buildCitationContext(List<SectionChunkRef> refs) {
    if (refs == null || refs.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (SectionChunkRef ref : refs) {
      if (ref == null || ref.getChunkId() == null) {
        continue;
      }
      String quote = ref.getQuoteText() == null ? "" : ref.getQuoteText().trim();
      if (quote.isBlank()) {
        continue;
      }
      if (quote.length() > 1200) {
        quote = quote.substring(0, 1200);
      }
      sb.append("- [chunk#")
          .append(ref.getChunkId())
          .append("] ")
          .append(quote)
          .append("\n");
      count++;
      if (count >= 10) {
        break;
      }
    }
    return sb.toString().trim();
  }

  @Transactional
  public AiTaskResponse autoWriteDocument(AiDocumentAutoWriteRequest request) {
    Long operatorId = currentUserService.getCurrentUserId();
    return documentAutoWriteService.autoWriteDocument(request, operatorId);
  }

  public AiTaskResponse getTask(Long taskId) {
    AiTask task = aiTaskRepository.findById(taskId).orElseThrow(EntityNotFoundException::new);
    return AiTaskResponse.from(task);
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
      // 1) 不得编造片段中不存在的事实；
      // 2) 如果信息不足，明确说"知识库暂无充分依据";
      String systemPrompt = """
          你是售前文档智能助手。你可以参考"知识片段"回答。
          要求：
          1) 回答尽量结构化、简洁，并给出可执行建议；
          2) 使用中文。
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

}
