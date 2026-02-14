package com.bidcollab.service;

import com.bidcollab.ai.AiClient;
import com.bidcollab.dto.AiAssistantAskRequest;
import com.bidcollab.dto.AiAssistantAskResponse;
import com.bidcollab.dto.AiAssistantCitation;
import com.bidcollab.dto.AiRewriteRequest;
import com.bidcollab.dto.KnowledgeSearchRequest;
import com.bidcollab.dto.KnowledgeSearchResult;
import com.bidcollab.dto.AiTaskResponse;
import com.bidcollab.dto.SectionVersionCreateRequest;
import com.bidcollab.entity.AiTask;
import com.bidcollab.entity.KnowledgeBase;
import com.bidcollab.entity.KnowledgeDocument;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionVersion;
import com.bidcollab.enums.AiTaskStatus;
import com.bidcollab.enums.SectionSourceType;
import com.bidcollab.repository.AiTaskRepository;
import com.bidcollab.repository.KnowledgeBaseRepository;
import com.bidcollab.repository.KnowledgeDocumentRepository;
import com.bidcollab.repository.SectionRepository;
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
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final KnowledgeDocumentRepository knowledgeDocumentRepository;
  private final KnowledgeBaseService knowledgeBaseService;
  private final AiClient aiClient;
  private final CurrentUserService currentUserService;

  public AiService(AiTaskRepository aiTaskRepository,
                   SectionRepository sectionRepository,
                   SectionVersionRepository sectionVersionRepository,
                   KnowledgeBaseRepository knowledgeBaseRepository,
                   KnowledgeDocumentRepository knowledgeDocumentRepository,
                   KnowledgeBaseService knowledgeBaseService,
                   AiClient aiClient,
                   CurrentUserService currentUserService) {
    this.aiTaskRepository = aiTaskRepository;
    this.sectionRepository = sectionRepository;
    this.sectionVersionRepository = sectionVersionRepository;
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    this.knowledgeBaseService = knowledgeBaseService;
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
      String systemPrompt = "你是售前招标文档助手，只能基于输入内容和项目参数改写，不得编造事实。";
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

  public AiTaskResponse getTask(Long taskId) {
    AiTask task = aiTaskRepository.findById(taskId).orElseThrow(EntityNotFoundException::new);
    return toResponse(task);
  }

  // [AI-READ] 检索增强问答：先检索知识片段，再让模型仅基于片段回答。
  public AiAssistantAskResponse ask(AiAssistantAskRequest request) {
    List<KnowledgeBase> targetBases = resolveTargetKnowledgeBases(request.getKnowledgeBaseId());
    if (targetBases.isEmpty()) {
      return AiAssistantAskResponse.builder()
          .answer("当前没有可用的知识库，请先创建并导入知识文档。")
          .matchedChunkCount(0)
          .citations(List.of())
          .build();
    }

    List<KnowledgeSearchResult> allHits = new ArrayList<>();
    Map<Long, Long> chunkToKb = new HashMap<>();
    for (KnowledgeBase kb : targetBases) {
      KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
      searchRequest.setQuery(request.getQuery());
      searchRequest.setTopK(Math.max(4, request.getTopK() == null ? 8 : request.getTopK()));
      searchRequest.setCandidateTopK(Math.max(12, searchRequest.getTopK() * 3));
      searchRequest.setMinScore(request.getMinScore() == null ? 0.15 : request.getMinScore());
      searchRequest.setRerank(Boolean.TRUE.equals(request.getRerank()));

      List<KnowledgeSearchResult> results = knowledgeBaseService.search(kb.getId(), searchRequest);
      for (KnowledgeSearchResult item : results) {
        allHits.add(item);
        chunkToKb.put(item.getChunkId(), kb.getId());
      }
    }

    allHits.sort(Comparator.comparingDouble(KnowledgeSearchResult::getScore).reversed());
    int contextTopK = Math.min(10, allHits.size());
    List<KnowledgeSearchResult> contextHits = allHits.subList(0, contextTopK);
    if (contextHits.isEmpty()) {
      return AiAssistantAskResponse.builder()
          .answer("未检索到足够相关的知识片段，请调整问题关键词后重试。")
          .matchedChunkCount(0)
          .citations(List.of())
          .build();
    }

    Map<Long, KnowledgeDocument> docById = new HashMap<>();
    for (KnowledgeDocument doc : knowledgeDocumentRepository.findAllById(
        contextHits.stream().map(KnowledgeSearchResult::getDocumentId).distinct().toList())) {
      docById.put(doc.getId(), doc);
    }
    Map<Long, KnowledgeBase> kbById = new HashMap<>();
    for (KnowledgeBase kb : targetBases) {
      kbById.put(kb.getId(), kb);
    }

    String context = buildContext(contextHits, chunkToKb, kbById, docById);
    String answer;
    try {
      String systemPrompt = """
          你是售前招标文档智能助手。你必须仅基于“知识片段”回答。
          要求：
          1) 不得编造片段中不存在的事实；
          2) 如果信息不足，明确说“知识库暂无充分依据”；
          3) 回答尽量结构化、简洁，并给出可执行建议；
          4) 使用中文。
          """;
      String userPrompt = "【用户问题】\n" + request.getQuery() + "\n\n【知识片段】\n" + context;
      answer = aiClient.chat(systemPrompt, userPrompt);
    } catch (Exception ex) {
      answer = "AI回答失败，请稍后重试。";
    }

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
          .snippet(snippet(hit.getContent(), 220))
          .build();
    }).toList();

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

  private AiTaskResponse toResponse(AiTask task) {
    return AiTaskResponse.builder()
        .id(task.getId())
        .sectionId(task.getSectionId())
        .sourceVersionId(task.getSourceVersionId())
        .resultVersionId(task.getResultVersionId())
        .status(task.getStatus())
        .errorMessage(task.getErrorMessage())
        .createdAt(task.getCreatedAt())
        .build();
  }
}
