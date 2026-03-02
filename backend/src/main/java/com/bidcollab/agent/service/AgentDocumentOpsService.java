package com.bidcollab.agent.service;

import com.bidcollab.agent.gateway.ModelGateway;
import com.bidcollab.agent.runtime.ReActContext;
import com.bidcollab.dto.KnowledgeSearchRequest;
import com.bidcollab.dto.KnowledgeSearchResult;
import com.bidcollab.entity.Document;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionVersion;
import com.bidcollab.enums.SectionSourceType;
import com.bidcollab.enums.SectionStatus;
import com.bidcollab.repository.DocumentRepository;
import com.bidcollab.repository.SectionRepository;
import com.bidcollab.repository.SectionVersionRepository;
import com.bidcollab.service.KnowledgeBaseService;
import com.bidcollab.service.SectionChunkRefService;
import com.bidcollab.util.StringUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ReAct 工具真正执行业务逻辑的服务层。
 */
@Service
@Slf4j
public class AgentDocumentOpsService {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static final String MEM_GLOBAL_HITS = "global_hits";
  public static final String MEM_LAST_SECTION_HITS = "last_section_hits";
  public static final String MEM_OUTLINE = "outline_nodes";
  public static final String MEM_OUTLINE_PERSISTED = "outline_persisted";
  public static final String MEM_FULL_DRAFT = "full_draft_sections";
  public static final String MEM_WRITTEN_SECTIONS = "written_sections";
  public static final String MEM_SECTION_CURSOR = "section_cursor";
  public static final String MEM_SECTION_TOTAL = "section_total";

  private final DocumentRepository documentRepository;
  private final SectionRepository sectionRepository;
  private final SectionVersionRepository sectionVersionRepository;
  private final KnowledgeBaseService knowledgeBaseService;
  private final SectionChunkRefService sectionChunkRefService;
  private final ModelGateway modelGateway;

  public AgentDocumentOpsService(DocumentRepository documentRepository,
      SectionRepository sectionRepository,
      SectionVersionRepository sectionVersionRepository,
      KnowledgeBaseService knowledgeBaseService,
      SectionChunkRefService sectionChunkRefService,
      ModelGateway modelGateway) {
    this.documentRepository = documentRepository;
    this.sectionRepository = sectionRepository;
    this.sectionVersionRepository = sectionVersionRepository;
    this.knowledgeBaseService = knowledgeBaseService;
    this.sectionChunkRefService = sectionChunkRefService;
    this.modelGateway = modelGateway;
  }

  public List<KnowledgeSearchResult> retrieveKnowledge(ReActContext context, Map<String, Object> args) {
    return retrieveKnowledgeInternal(context, args, true);
  }

  private List<KnowledgeSearchResult> retrieveKnowledgeInternal(ReActContext context, Map<String, Object> args,
      boolean storeAsGlobal) {
    if (context.getKnowledgeBaseId() == null) {
      return List.of();
    }
    Long operatorId = context.getOperatorId();
    if (operatorId == null) {
      throw new IllegalStateException("Current user is required for knowledge retrieval");
    }
    int topK = asInt(args.get("topK"), 8);
    int candidateTopK = asInt(args.get("candidateTopK"), Math.max(16, topK * 2));
    double minScore = asDouble(args.get("minScore"), 0.05);
    boolean rerank = asBoolean(args.get("rerank"), true);
    String query = resolveQuery(context, args);
    KnowledgeSearchRequest request = new KnowledgeSearchRequest();
    request.setQuery(query);
    request.setTopK(topK);
    request.setCandidateTopK(candidateTopK);
    request.setMinScore(minScore);
    request.setRerank(rerank);
    List<KnowledgeSearchResult> hits = knowledgeBaseService.searchAsUser(context.getKnowledgeBaseId(), request,
        operatorId);
    if (storeAsGlobal) {
      context.getMemory().put(MEM_GLOBAL_HITS, hits);
    } else {
      context.getMemory().put(MEM_LAST_SECTION_HITS, hits);
    }
    return hits;
  }

  public List<OutlineNode> generateOutline(ReActContext context, Map<String, Object> args) {
    List<Section> existing = sectionRepository.findTreeByDocumentId(context.getDocumentId());
    if (!existing.isEmpty()) {
      List<OutlineNode> reused = toOutlineNodes(existing);
      context.getMemory().put(MEM_OUTLINE, reused);
      return reused;
    }
    List<KnowledgeSearchResult> hits = getGlobalHits(context);
    String retrievalContext = toRetrievalContext(hits, 12);
    String systemPrompt = """
        你是文档架构师。请输出文档大纲。
        返回 JSON:
        {"outline":[{"title":"章节","children":[{"title":"小节"}]}]}
        要求：
        1) 中文标题，简洁专业；
        2) 一级章节 3-8 个；
        3) 不输出空 children。
        """;
    String userPrompt = """
        文档需求：%s
        项目参数：%s
        参考知识：%s
        """.formatted(
        safe(context.getRequirement(), "请根据需求自动规划文档大纲"),
        safe(context.getProjectParams(), "(无)"),
        retrievalContext.isBlank() ? "(无)" : retrievalContext);
    String raw = modelGateway.chat(systemPrompt, userPrompt);
    List<OutlineNode> nodes = parseOutline(raw);
    if (nodes.isEmpty()) {
      nodes = List.of(new OutlineNode("方案概述", List.of()));
    }
    context.getMemory().put(MEM_OUTLINE, nodes);
    return nodes;
  }

  public int persistOutline(ReActContext context, Map<String, Object> args) {
    if (context.getSectionId() != null) {
      return 0;
    }
    List<Section> existing = sectionRepository.findTreeByDocumentId(context.getDocumentId());
    if (!existing.isEmpty()) {
      context.getMemory().put(MEM_OUTLINE_PERSISTED, Boolean.TRUE);
      return 0;
    }
    @SuppressWarnings("unchecked")
    List<OutlineNode> nodes = (List<OutlineNode>) context.getMemory().get(MEM_OUTLINE);
    if (nodes == null || nodes.isEmpty()) {
      throw new IllegalStateException("Outline not found, cannot persist");
    }
    Document document = documentRepository.findById(context.getDocumentId()).orElseThrow(EntityNotFoundException::new);
    int created = saveOutlineNodes(document, null, nodes, 1, context.getOperatorId(), 0);
    context.getMemory().put(MEM_OUTLINE_PERSISTED, Boolean.TRUE);
    return created;
  }

  public int composeSections(ReActContext context, Map<String, Object> args) {
    List<Section> targets = resolveTargetSections(context);
    int total = targets.size();
    int cursor = currentSectionCursor(context);
    int batchSize = Math.max(1, Math.min(5, asInt(args.get("batchSize"), 2)));
    int end = Math.min(total, cursor + batchSize);
    int success = 0;
    int failed = 0;

    if (cursor >= total) {
      context.getMemory().put(MEM_SECTION_TOTAL, total);
      return 0;
    }

    for (int i = cursor; i < end; i++) {
      if (Thread.currentThread().isInterrupted()) {
        throw new IllegalStateException("compose_sections interrupted");
      }
      Section section = targets.get(i);
      try {
        List<KnowledgeSearchResult> hits = retrieveBySection(context, section);
        if (hits.isEmpty()) {
          hits = getGlobalHits(context).stream().limit(8).toList();
        }
        GeneratedSection generated = generateSectionContent(context, section, hits);
        List<KnowledgeSearchResult> refs = resolveUsedHits(generated.usedChunkIds(), hits);
        persistSection(context, section, generated.contentHtml(), refs,
            "agent-react-task-" + context.getTaskId());
        log.info("[Agent][Task:{}] section composed: sectionId={}, title={}, hits={}, refs={}",
            context.getTaskId(), section.getId(), section.getTitle(), hits.size(), refs.size());
        success++;
      } catch (Exception ex) {
        failed++;
        context.addObservation("SECTION[" + section.getTitle() + "] FAILED: " + safe(ex.getMessage(), "unknown"));
        log.warn("[Agent][Task:{}] compose section failed: sectionId={}, title={}",
            context.getTaskId(), section.getId(), section.getTitle(), ex);
      } finally {
        context.getMemory().put(MEM_SECTION_CURSOR, i + 1);
      }
    }

    int written = asInt(context.getMemory().get(MEM_WRITTEN_SECTIONS), 0);
    context.getMemory().put(MEM_WRITTEN_SECTIONS, written + success);
    context.getMemory().put(MEM_SECTION_TOTAL, total);

    if (success == 0 && failed > 0) {
      throw new IllegalStateException("章节批次全部失败");
    }
    return success;
  }

  public int composeFullDraft(ReActContext context, Map<String, Object> args) {
    List<KnowledgeSearchResult> hits = getGlobalHits(context);
    String systemPrompt = """
        你是文档写作助手，请先生成整篇初稿。
        返回 JSON:
        {"sections":[{"title":"章节标题","contentHtml":"<p>正文</p>","usedChunkIds":[1,2]}]}
        要求：
        1) 标题和正文中文输出；
        2) 正文允许 HTML 标签 p/ul/li/table/img 等；
        3) usedChunkIds 必须来自输入片段中的 chunkId。
        """;
    String userPrompt = """
        文档需求：%s
        项目参数：%s
        参考知识：%s
        """.formatted(
        safe(context.getRequirement(), "请先生成完整文档初稿"),
        safe(context.getProjectParams(), "(无)"),
        toRetrievalContext(hits, 16));
    String raw = modelGateway.chat(systemPrompt, userPrompt);
    List<GeneratedSection> sections = parseSections(raw);
    context.getMemory().put(MEM_FULL_DRAFT, sections);
    return sections.size();
  }

  public int splitPersistDraft(ReActContext context, Map<String, Object> args) {
    @SuppressWarnings("unchecked")
    List<GeneratedSection> drafts = (List<GeneratedSection>) context.getMemory().get(MEM_FULL_DRAFT);
    if (drafts == null || drafts.isEmpty()) {
      throw new IllegalStateException("Full draft not found");
    }
    List<Section> sections = sectionRepository.findTreeByDocumentId(context.getDocumentId());
    if (sections.isEmpty() && context.getSectionId() == null) {
      Document doc = documentRepository.findById(context.getDocumentId()).orElseThrow(EntityNotFoundException::new);
      int sortIndex = 0;
      for (GeneratedSection draft : drafts) {
        if (draft.title() == null || draft.title().isBlank()) {
          continue;
        }
        sectionRepository.save(Section.builder()
            .document(doc)
            .parent(null)
            .title(draft.title())
            .level(1)
            .sortIndex(sortIndex++)
            .status(SectionStatus.DRAFT)
            .createdBy(context.getOperatorId())
            .build());
      }
      sections = sectionRepository.findTreeByDocumentId(context.getDocumentId());
    }
    Map<String, Section> byTitle = new LinkedHashMap<>();
    for (Section section : sections) {
      byTitle.put(normalizeTitle(section.getTitle()), section);
    }
    List<KnowledgeSearchResult> globalHits = getGlobalHits(context);
    int persisted = 0;
    for (GeneratedSection draft : drafts) {
      Section target = byTitle.get(normalizeTitle(draft.title()));
      if (target == null) {
        continue;
      }
      persistSection(context, target, draft.contentHtml(), resolveUsedHits(draft.usedChunkIds(), globalHits),
          "agent-react-fast-draft-" + context.getTaskId());
      persisted++;
    }
    context.getMemory().put(MEM_WRITTEN_SECTIONS, persisted);
    return persisted;
  }

  public boolean hasOutline(ReActContext context) {
    Object value = context.getMemory().get(MEM_OUTLINE);
    return value instanceof List<?> list && !list.isEmpty();
  }

  public boolean hasGlobalHits(ReActContext context) {
    return !getGlobalHits(context).isEmpty();
  }

  public boolean isOutlinePersisted(ReActContext context) {
    Object value = context.getMemory().get(MEM_OUTLINE_PERSISTED);
    return Boolean.TRUE.equals(value);
  }

  public boolean hasFullDraft(ReActContext context) {
    Object value = context.getMemory().get(MEM_FULL_DRAFT);
    return value instanceof List<?> list && !list.isEmpty();
  }

  public int writtenSectionCount(ReActContext context) {
    Object value = context.getMemory().get(MEM_WRITTEN_SECTIONS);
    return value instanceof Number number ? number.intValue() : 0;
  }

  public int totalSectionCount(ReActContext context) {
    Object value = context.getMemory().get(MEM_SECTION_TOTAL);
    if (value instanceof Number number) {
      return number.intValue();
    }
    int total = resolveTargetSections(context).size();
    context.getMemory().put(MEM_SECTION_TOTAL, total);
    return total;
  }

  public int currentSectionCursor(ReActContext context) {
    Object value = context.getMemory().get(MEM_SECTION_CURSOR);
    if (value instanceof Number number) {
      return Math.max(0, number.intValue());
    }
    return 0;
  }

  public boolean hasPendingSections(ReActContext context) {
    return currentSectionCursor(context) < totalSectionCount(context);
  }

  private int saveOutlineNodes(Document document, Section parent, List<OutlineNode> nodes, int level,
      Long operatorId, int startSortIndex) {
    int sort = startSortIndex;
    int created = 0;
    for (OutlineNode node : nodes) {
      if (node.title() == null || node.title().isBlank()) {
        continue;
      }
      Section section = Section.builder()
          .document(document)
          .parent(parent)
          .title(node.title())
          .level(level)
          .sortIndex(sort++)
          .status(SectionStatus.DRAFT)
          .createdBy(operatorId)
          .build();
      sectionRepository.save(section);
      created++;
      if (node.children() != null && !node.children().isEmpty()) {
        created += saveOutlineNodes(document, section, node.children(), level + 1, operatorId, 0);
      }
    }
    return created;
  }

  private GeneratedSection generateSectionContent(ReActContext context, Section section,
      List<KnowledgeSearchResult> hits) {
    String systemPrompt = """
        你是企业级文档助手。
        返回 JSON:
        {"html":"<p>章节正文</p>","usedChunkIds":[1,2]}
        规则：
        1) 语言必须是中文。
        2) 只围绕“当前章节”写作，禁止跨章节扩展；
        3) 优先复用“知识片段”中的事实、术语、数字，禁止编造；
        4) 若片段不足，允许最小必要补充，并明确写“基于现有片段”；
        5) 不输出章节标题本身，不输出```代码块```；
        6) 输出可直接编辑的 HTML（允许 p,strong,em,u,span,br,ul,ol,li,table,thead,tbody,tr,th,td,blockquote,img）；
        7) usedChunkIds 只能来自输入片段；
        8) 不要在正文中输出 [chunk#123] 这类引用标记。
        9) 若知识片段包含图片标记 [img ...]URL，且你需要使用的话，需要在 html 中转成 <img src="URL" alt="图片说明" />。
        """;
    String userPrompt = """
        章节标题：%s
        章节路径：%s
        写作需求：%s
        项目参数：%s
        知识片段：%s
        """.formatted(
        section.getTitle(),
        buildSectionPath(section),
        safe(context.getRequirement(), "生成专业、可直接提交的章节正文"),
        safe(context.getProjectParams(), "(无)"),
        toRetrievalContext(hits, 10));
    String raw = modelGateway.chat(systemPrompt, userPrompt);
    JsonNode node = readJson(raw);
    if (node == null) {
      return new GeneratedSection(section.getTitle(), ensureHtml(StringUtil.safe(raw)), defaultChunkIds(hits, 6));
    }
    String html = StringUtil.safe(node.path("html").asText(""));
    if (html.isBlank()) {
      html = StringUtil.safe(node.path("contentHtml").asText(""));
    }
    html = ensureHtml(html);
    List<Long> usedChunkIds = parseLongArray(node.path("usedChunkIds"));
    if (usedChunkIds.isEmpty()) {
      usedChunkIds = defaultChunkIds(hits, 6);
    }
    return new GeneratedSection(section.getTitle(), html, usedChunkIds);
  }

  private void persistSection(ReActContext context, Section section, String html, List<KnowledgeSearchResult> refs,
      String sourceRef) {
    String content = StringUtil.safe(html);
    if (content.isBlank()) {
      return;
    }
    SectionVersion version = section.getCurrentVersion();
    if (version == null) {
      version = SectionVersion.builder()
          .section(section)
          .content(content)
          .summary("AI自动编写")
          .sourceType(SectionSourceType.AI)
          .sourceRef(sourceRef)
          .createdBy(context.getOperatorId())
          .build();
    } else {
      version.setContent(content);
      version.setSummary("AI自动编写");
      version.setSourceType(SectionSourceType.AI);
      version.setSourceRef(sourceRef);
      version.setCreatedBy(context.getOperatorId());
    }
    sectionVersionRepository.save(version);
    section.setCurrentVersion(version);
    sectionRepository.save(section);
    sectionChunkRefService.replaceByHits(section, version, refs, context.getOperatorId());
  }

  private List<Section> resolveTargetSections(ReActContext context) {
    List<Section> all = sectionRepository.findTreeByDocumentId(context.getDocumentId());
    if (context.getSectionId() != null) {
      return all.stream().filter(s -> s.getId().equals(context.getSectionId())).toList();
    }
    return orderSections(all);
  }

  private List<Section> orderSections(List<Section> sections) {
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
    for (List<Section> list : children.values()) {
      list.sort(Comparator.comparing(Section::getSortIndex));
    }
    List<Section> ordered = new ArrayList<>();
    for (Section root : roots) {
      walkSection(root, children, ordered);
    }
    return ordered;
  }

  private void walkSection(Section current, Map<Long, List<Section>> children, List<Section> result) {
    result.add(current);
    for (Section child : children.getOrDefault(current.getId(), List.of())) {
      walkSection(child, children, result);
    }
  }

  private List<KnowledgeSearchResult> retrieveBySection(ReActContext context, Section section) {
    if (context.getKnowledgeBaseId() == null) {
      return List.of();
    }
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("query", buildSectionPath(section) + " " + section.getTitle() + " " + safe(context.getRequirement(), ""));
    args.put("topK", 6);
    args.put("candidateTopK", 18);
    args.put("minScore", 0.08);
    args.put("rerank", true);
    return retrieveKnowledgeInternal(context, args, false);
  }

  private List<KnowledgeSearchResult> getGlobalHits(ReActContext context) {
    Object value = context.getMemory().get(MEM_GLOBAL_HITS);
    if (value instanceof List<?> list) {
      return list.stream()
          .filter(KnowledgeSearchResult.class::isInstance)
          .map(KnowledgeSearchResult.class::cast)
          .toList();
    }
    return List.of();
  }

  private String resolveQuery(ReActContext context, Map<String, Object> args) {
    String query = asString(args.get("query"));
    if (!query.isBlank()) {
      return query;
    }
    Document doc = documentRepository.findById(context.getDocumentId()).orElseThrow(EntityNotFoundException::new);
    return (doc.getName() + " " + safe(context.getRequirement(), "") + " " + safe(context.getProjectParams(), ""))
        .replaceAll("\\s+", " ").trim();
  }

  private List<OutlineNode> parseOutline(String raw) {
    JsonNode root = readJson(raw);
    if (root == null || !root.path("outline").isArray()) {
      return List.of();
    }
    List<OutlineNode> nodes = new ArrayList<>();
    for (JsonNode node : root.path("outline")) {
      OutlineNode mapped = parseOutlineNode(node);
      if (mapped != null) {
        nodes.add(mapped);
      }
    }
    return nodes;
  }

  private OutlineNode parseOutlineNode(JsonNode node) {
    String title = StringUtil.safe(node.path("title").asText(""));
    if (title.isBlank()) {
      return null;
    }
    List<OutlineNode> children = new ArrayList<>();
    JsonNode childNode = node.path("children");
    if (childNode.isArray()) {
      for (JsonNode child : childNode) {
        OutlineNode parsed = parseOutlineNode(child);
        if (parsed != null) {
          children.add(parsed);
        }
      }
    }
    return new OutlineNode(title, children);
  }

  private List<GeneratedSection> parseSections(String raw) {
    JsonNode root = readJson(raw);
    if (root == null || !root.path("sections").isArray()) {
      return List.of();
    }
    List<GeneratedSection> results = new ArrayList<>();
    for (JsonNode node : root.path("sections")) {
      String title = StringUtil.safe(node.path("title").asText(""));
      String contentHtml = StringUtil.safe(node.path("contentHtml").asText(""));
      if (contentHtml.isBlank()) {
        contentHtml = StringUtil.safe(node.path("html").asText(""));
      }
      if (title.isBlank() || contentHtml.isBlank()) {
        continue;
      }
      List<Long> used = parseLongArray(node.path("usedChunkIds"));
      results.add(new GeneratedSection(title, contentHtml, used));
    }
    return results;
  }

  private JsonNode readJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String text = StringUtil.safe(raw);
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

  private String toRetrievalContext(List<KnowledgeSearchResult> hits, int limit) {
    if (hits == null || hits.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (KnowledgeSearchResult hit : hits) {
      if (hit == null || hit.getChunkId() == null) {
        continue;
      }
      sb.append("- [chunk#").append(hit.getChunkId()).append("] ")
          .append(normalize(hit.getContent()))
          .append('\n');
      count++;
      if (count >= limit) {
        break;
      }
    }
    return sb.toString().trim();
  }

  private String normalize(String content) {
    String text = StringUtil.safe(content).replaceAll("\\s+", " ").trim();
    if (text.length() > 900) {
      return text.substring(0, 900);
    }
    return text;
  }

  private String ensureHtml(String htmlOrText) {
    String text = StringUtil.safe(htmlOrText).trim();
    if (text.isBlank()) {
      return "";
    }
    if (text.contains("<p") || text.contains("<ul") || text.contains("<ol") || text.contains("<table")
        || text.contains("<img") || text.contains("<blockquote")) {
      return text;
    }
    return "<p>" + text.replace("\n", "<br/>") + "</p>";
  }

  private String buildSectionPath(Section section) {
    List<String> titles = new ArrayList<>();
    Section cursor = section;
    int guard = 0;
    while (cursor != null && guard++ < 32) {
      String title = StringUtil.safe(cursor.getTitle());
      if (!title.isBlank()) {
        titles.add(0, title);
      }
      cursor = cursor.getParent();
    }
    return String.join(" > ", titles);
  }

  private List<OutlineNode> toOutlineNodes(List<Section> sections) {
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
    for (List<Section> list : children.values()) {
      list.sort(Comparator.comparing(Section::getSortIndex));
    }
    List<OutlineNode> results = new ArrayList<>();
    for (Section root : roots) {
      results.add(toOutlineNode(root, children));
    }
    return results;
  }

  private OutlineNode toOutlineNode(Section section, Map<Long, List<Section>> children) {
    List<OutlineNode> childNodes = new ArrayList<>();
    for (Section child : children.getOrDefault(section.getId(), List.of())) {
      childNodes.add(toOutlineNode(child, children));
    }
    return new OutlineNode(section.getTitle(), childNodes);
  }

  private List<Long> parseLongArray(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<Long> values = new ArrayList<>();
    for (JsonNode item : node) {
      if (item.isIntegralNumber()) {
        values.add(item.asLong());
      } else if (item.isTextual()) {
        try {
          values.add(Long.parseLong(item.asText().trim()));
        } catch (Exception ignored) {
        }
      }
    }
    return values;
  }

  private List<Long> defaultChunkIds(List<KnowledgeSearchResult> hits, int limit) {
    List<Long> ids = new ArrayList<>();
    if (hits == null) {
      return ids;
    }
    for (KnowledgeSearchResult hit : hits) {
      if (hit.getChunkId() == null) {
        continue;
      }
      ids.add(hit.getChunkId());
      if (ids.size() >= limit) {
        break;
      }
    }
    return ids;
  }

  private List<KnowledgeSearchResult> resolveUsedHits(List<Long> chunkIds, List<KnowledgeSearchResult> hits) {
    if (hits == null || hits.isEmpty()) {
      return List.of();
    }
    if (chunkIds == null || chunkIds.isEmpty()) {
      return hits.stream().limit(8).toList();
    }
    Map<Long, KnowledgeSearchResult> byId = new LinkedHashMap<>();
    for (KnowledgeSearchResult hit : hits) {
      if (hit.getChunkId() != null) {
        byId.put(hit.getChunkId(), hit);
      }
    }
    Set<Long> dedup = new LinkedHashSet<>();
    List<KnowledgeSearchResult> refs = new ArrayList<>();
    for (Long id : chunkIds) {
      if (id == null || !dedup.add(id)) {
        continue;
      }
      KnowledgeSearchResult hit = byId.get(id);
      if (hit != null) {
        refs.add(hit);
      }
      if (refs.size() >= 12) {
        break;
      }
    }
    return refs.isEmpty() ? hits.stream().limit(8).toList() : refs;
  }

  private int asInt(Object value, int defaultValue) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text) {
      try {
        return Integer.parseInt(text.trim());
      } catch (Exception ignored) {
      }
    }
    return defaultValue;
  }

  private double asDouble(Object value, double defaultValue) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String text) {
      try {
        return Double.parseDouble(text.trim());
      } catch (Exception ignored) {
      }
    }
    return defaultValue;
  }

  private boolean asBoolean(Object value, boolean defaultValue) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof String text) {
      String v = text.trim().toLowerCase();
      if (Objects.equals(v, "true") || Objects.equals(v, "1")) {
        return true;
      }
      if (Objects.equals(v, "false") || Objects.equals(v, "0")) {
        return false;
      }
    }
    return defaultValue;
  }

  private String asString(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private String safe(String value, String fallback) {
    String text = StringUtil.safe(value);
    return text.isBlank() ? fallback : text;
  }

  private String normalizeTitle(String title) {
    return StringUtil.safe(title).replaceAll("\\s+", "");
  }

  public record OutlineNode(String title, List<OutlineNode> children) {
  }

  public record GeneratedSection(String title, String contentHtml, List<Long> usedChunkIds) {
  }
}
