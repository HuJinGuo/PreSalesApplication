package com.bidcollab.service;

import com.bidcollab.ai.AiClient;
import com.bidcollab.dto.KnowledgeBaseCreateRequest;
import com.bidcollab.dto.KnowledgeBaseResponse;
import com.bidcollab.dto.KnowledgeChunkResponse;
import com.bidcollab.dto.KnowledgeDocumentGrantRequest;
import com.bidcollab.dto.KnowledgeDocumentResponse;
import com.bidcollab.dto.KnowledgeDocumentVisibilityUpdateRequest;
import com.bidcollab.dto.KnowledgeManualContentRequest;
import com.bidcollab.dto.KnowledgeSearchRequest;
import com.bidcollab.dto.KnowledgeSearchResult;
import com.bidcollab.dto.graph.KnowledgeGraphDetailChunk;
import com.bidcollab.dto.graph.KnowledgeGraphDetailDocument;
import com.bidcollab.dto.graph.KnowledgeGraphEdge;
import com.bidcollab.dto.graph.KnowledgeGraphNode;
import com.bidcollab.dto.graph.KnowledgeGraphNodeDetailResponse;
import com.bidcollab.dto.graph.KnowledgeGraphResponse;
import com.bidcollab.entity.KnowledgeBase;
import com.bidcollab.entity.KnowledgeBaseDomainLexicon;
import com.bidcollab.entity.KnowledgeChunk;
import com.bidcollab.entity.KnowledgeDocument;
import com.bidcollab.entity.KnowledgeDocumentPermission;
import com.bidcollab.entity.ExamPaper;
import com.bidcollab.enums.KnowledgeVisibility;
import com.bidcollab.repository.ExamPaperRepository;
import com.bidcollab.repository.ExamQuestionRepository;
import com.bidcollab.repository.ExamSubmissionRepository;
import com.bidcollab.repository.KnowledgeBaseRepository;
import com.bidcollab.repository.KnowledgeBaseDomainLexiconRepository;
import com.bidcollab.repository.KnowledgeChunkRepository;
import com.bidcollab.repository.KnowledgeDocumentPermissionRepository;
import com.bidcollab.repository.KnowledgeDocumentRepository;
import com.bidcollab.repository.UserRepository;
import com.bidcollab.util.DocumentTextExtractor;
import com.bidcollab.util.TextChunker;
import com.bidcollab.util.VectorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

/**
 * 知识库核心服务。
 * <p>
 * 提供知识库的 CRUD、文档管理（上传/手动录入）、权限控制、
 * 向量语义搜索（含 AI Rerank）、以及轻量级知识图谱构建能力。
 * <p>
 * 知识图谱采用纯 MySQL 实现方案（无需图数据库），
 * 通过基于词典的领域实体识别（NER）和句子级共现分析提取实体关系。
 * <p>
 * 注：带有 [AI-READ] 标识的注释用于学习导读，可按该标识批量检索/删除。
 */
@Service
public class KnowledgeBaseService {
  /** JSON 序列化工具 */
  private static final ObjectMapper MAPPER = new ObjectMapper();
  /** 分词正则：按非汉字/字母/数字字符切分 */
  private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsHan}A-Za-z0-9]+");
  /** 停用词表：过滤无信息量的高频词 */
  private static final Set<String> STOPWORDS = Set.of(
      "the", "and", "for", "with", "that", "this", "from", "have", "will", "shall",
      "项目", "系统", "进行", "以及", "我们", "你们", "他们", "可以", "通过", "工作", "方案", "服务",
      "每年", "每月", "每周", "每季度", "每半月", "每半年", "所示", "如下", "相关", "注意事项", "说明");
  private final KnowledgeBaseRepository baseRepository;
  private final KnowledgeBaseDomainLexiconRepository lexiconRepository;
  private final KnowledgeDocumentRepository documentRepository;
  private final KnowledgeChunkRepository chunkRepository;
  private final KnowledgeDocumentPermissionRepository permissionRepository;
  private final ExamPaperRepository examPaperRepository;
  private final ExamQuestionRepository examQuestionRepository;
  private final ExamSubmissionRepository examSubmissionRepository;
  private final UserRepository userRepository;
  private final CurrentUserService currentUserService;
  private final DocumentTextExtractor textExtractor;
  private final TextChunker textChunker;
  private final AiClient aiClient;
  private final String knowledgeStorageDir;
  private final String uploadStorageDir;
  /** Embedding 并行线程池，控制并发数避免压垮 AI API */
  private final ExecutorService embeddingExecutor;

  public KnowledgeBaseService(KnowledgeBaseRepository baseRepository,
      KnowledgeBaseDomainLexiconRepository lexiconRepository,
      KnowledgeDocumentRepository documentRepository,
      KnowledgeChunkRepository chunkRepository,
      KnowledgeDocumentPermissionRepository permissionRepository,
      ExamPaperRepository examPaperRepository,
      ExamQuestionRepository examQuestionRepository,
      ExamSubmissionRepository examSubmissionRepository,
      UserRepository userRepository,
      CurrentUserService currentUserService,
      DocumentTextExtractor textExtractor,
      TextChunker textChunker,
      AiClient aiClient,
      @Value("${app.knowledge.storage-dir:/tmp/bid-knowledge-files}") String knowledgeStorageDir,
      @Value("${app.upload.storage-dir:/tmp/bid-doc-uploads}") String uploadStorageDir,
      @Qualifier("embeddingExecutor") ExecutorService embeddingExecutor) {
    this.baseRepository = baseRepository;
    this.lexiconRepository = lexiconRepository;
    this.documentRepository = documentRepository;
    this.chunkRepository = chunkRepository;
    this.permissionRepository = permissionRepository;
    this.examPaperRepository = examPaperRepository;
    this.examQuestionRepository = examQuestionRepository;
    this.examSubmissionRepository = examSubmissionRepository;
    this.userRepository = userRepository;
    this.currentUserService = currentUserService;
    this.textExtractor = textExtractor;
    this.textChunker = textChunker;
    this.aiClient = aiClient;
    this.knowledgeStorageDir = knowledgeStorageDir;
    this.uploadStorageDir = uploadStorageDir;
    this.embeddingExecutor = embeddingExecutor;
  }

  /**
   * 创建新的知识库。
   *
   * @param request 包含知识库名称和描述的请求对象
   * @return 创建成功的知识库响应信息
   */
  @Transactional
  public KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request) {
    KnowledgeBase kb = KnowledgeBase.builder()
        .name(request.getName())
        .description(request.getDescription())
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    baseRepository.save(kb);
    return toBaseResponse(kb);
  }

  /**
   * 列出所有知识库。
   *
   * @return 所有知识库的列表
   */
  public List<KnowledgeBaseResponse> list() {
    return baseRepository.findAll().stream().map(this::toBaseResponse).collect(Collectors.toList());
  }

  // [AI-READ] 词典更新后重建知识库索引：上传文档会优先从本地原文件重提取，保证一致性。
  @Transactional
  public void reindexKnowledgeBase(Long kbId) {
    baseRepository.findById(kbId).orElseThrow(EntityNotFoundException::new);
    List<KnowledgeDocument> docs = documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId);
    for (KnowledgeDocument doc : docs) {
      if ("UPLOAD".equalsIgnoreCase(doc.getSourceType())
          && doc.getStoragePath() != null
          && !doc.getStoragePath().isBlank()) {
        doc.setContent(extractTextFromStoredFile(doc));
      }
      reindexDocument(doc);
    }
  }

  /**
   * 删除指定的知识库。
   * <p>
   * 注意：这是一个级联删除操作，会删除该知识库下的所有文档、权限、向量块、试卷及考试记录。
   * 只有知识库的创建者有权执行此操作。
   *
   * @param kbId 知识库ID
   * @throws IllegalStateException 如果当前用户不是知识库的所有者
   */
  @Transactional
  public void delete(Long kbId) {
    KnowledgeBase kb = baseRepository.findById(kbId).orElseThrow(EntityNotFoundException::new);
    Long userId = currentUserService.getCurrentUserId();
    if (userId == null || !userId.equals(kb.getCreatedBy())) {
      throw new IllegalStateException("Only knowledge base owner can delete it");
    }

    List<KnowledgeDocument> docs = documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId);
    for (KnowledgeDocument doc : docs) {
      permissionRepository.deleteByKnowledgeDocumentId(doc.getId());
      chunkRepository.deleteByKnowledgeDocumentId(doc.getId());
    }

    List<ExamPaper> papers = examPaperRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId);
    for (ExamPaper paper : papers) {
      examSubmissionRepository.deleteByPaperId(paper.getId());
      examQuestionRepository.deleteByPaperId(paper.getId());
    }
    examPaperRepository.deleteAll(papers);

    documentRepository.deleteByKnowledgeBaseId(kbId);
    chunkRepository.deleteByKnowledgeBaseId(kbId);
    lexiconRepository.deleteByKnowledgeBaseId(kbId);
    baseRepository.deleteById(kbId);
  }

  /**
   * 手动添加知识内容。
   * <p>
   * 创建一个类型为 "MANUAL" 的知识文档，并对其进行索引（分块和向量化）。
   *
   * @param kbId    知识库ID
   * @param request 包含标题和内容的请求对象
   * @return 创建成功的文档响应信息
   */
  @Transactional
  public KnowledgeDocumentResponse addManualContent(Long kbId, KnowledgeManualContentRequest request) {
    KnowledgeBase kb = baseRepository.findById(kbId).orElseThrow(EntityNotFoundException::new);
    KnowledgeDocument doc = KnowledgeDocument.builder()
        .knowledgeBase(kb)
        .title(request.getTitle())
        .sourceType("MANUAL")
        .content(request.getContent())
        .visibility(KnowledgeVisibility.PRIVATE)
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    documentRepository.save(doc);
    reindexDocument(doc);
    return toDocumentResponse(doc);
  }

  /**
   * 上传并解析文件到知识库。
   * <p>
   * 支持 Word, PDF 等格式。解析后的文本内容会被保存并建立索引。
   *
   * @param kbId 知识库ID
   * @param file 上传的文件
   * @return 创建成功的文档响应信息
   */
  @Transactional
  public KnowledgeDocumentResponse upload(Long kbId, MultipartFile file) {
    KnowledgeBase kb = baseRepository.findById(kbId).orElseThrow(EntityNotFoundException::new);
    String fileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
    String fileType = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "bin";
    String storagePath = storeUploadedFile(kbId, file);

    KnowledgeDocument doc = KnowledgeDocument.builder()
        .knowledgeBase(kb)
        .title(fileName)
        .sourceType("UPLOAD")
        .fileName(fileName)
        .fileType(fileType)
        .storagePath(storagePath)
        .content("")
        .visibility(KnowledgeVisibility.PRIVATE)
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    documentRepository.save(doc);
    doc.setContent(extractTextFromStoredFile(doc));
    // [AI-READ] 上传完成后立即重建向量索引，保证“上传即检索可用”。
    reindexDocument(doc);
    return toDocumentResponse(doc);
  }

  /**
   * 更新文档的可见性设置（私有/公开）。
   *
   * @param kbId       知识库ID
   * @param documentId 文档ID
   * @param request    包含新可见性状态的请求对象
   * @return 更新后的文档响应信息
   */
  @Transactional
  public KnowledgeDocumentResponse updateVisibility(Long kbId, Long documentId,
      KnowledgeDocumentVisibilityUpdateRequest request) {
    KnowledgeDocument doc = getDocumentInKb(kbId, documentId);
    assertDocumentOwner(doc);
    doc.setVisibility(request.getVisibility());
    return toDocumentResponse(doc);
  }

  /**
   * 授予指定用户对文档的访问权限。
   *
   * @param kbId       知识库ID
   * @param documentId 文档ID
   * @param request    包含目标用户ID的请求对象
   */
  @Transactional
  public void grantAccess(Long kbId, Long documentId, KnowledgeDocumentGrantRequest request) {
    KnowledgeDocument doc = getDocumentInKb(kbId, documentId);
    assertDocumentOwner(doc);
    userRepository.findById(request.getUserId()).orElseThrow(EntityNotFoundException::new);
    boolean exists = permissionRepository.findByKnowledgeDocumentIdAndUserId(doc.getId(), request.getUserId())
        .isPresent();
    if (!exists) {
      permissionRepository.save(KnowledgeDocumentPermission.builder()
          .knowledgeDocumentId(doc.getId())
          .userId(request.getUserId())
          .createdBy(currentUserService.getCurrentUserId())
          .build());
    }
  }

  /**
   * 列出指定知识库中当前用户有权访问的所有文档。
   *
   * @param kbId 知识库ID
   * @return 文档列表
   */
  public List<KnowledgeDocumentResponse> listDocuments(Long kbId) {
    Long userId = currentUserService.getCurrentUserId();
    return documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId)
        .stream()
        .filter(doc -> hasReadAccess(doc, userId))
        .map(this::toDocumentResponse)
        .collect(Collectors.toList());
  }

  /**
   * 列出指定知识库中所有文档的所有分块（Chunk）。
   * <p>
   * 仅包含当前用户有权访问的文档的分块。
   *
   * @param kbId 知识库ID
   * @return 知识块列表
   */
  public List<KnowledgeChunkResponse> listChunks(Long kbId) {
    Set<Long> allowedDocIds = listAccessibleDocumentIds(kbId, currentUserService.getCurrentUserId());
    return chunkRepository.findByKnowledgeBaseId(kbId).stream()
        .filter(chunk -> chunk.getKnowledgeDocument() != null
            && allowedDocIds.contains(chunk.getKnowledgeDocument().getId()))
        .map(this::toChunkResponse)
        .collect(Collectors.toList());
  }

  /**
   * 在知识库中执行向量语义搜索。
   * <p>
   * 流程：
   * 1. 过滤权限范围内的文档。
   * 2. 计算查询词的向量。
   * 3. 在允许的文档范围内计算 Cosine 相似度。
   * 4. (可选) 使用 LLM 进行二次重排（Rerank）。
   *
   * @param kbId    知识库ID
   * @param request 搜索请求（包含查询词、文档范围、TopK等）
   * @return 搜索结果列表，按相关度排序
   */
  public List<KnowledgeSearchResult> search(Long kbId, KnowledgeSearchRequest request) {
    Long userId = currentUserService.getCurrentUserId();
    // 权限前置过滤：后续向量计算只在允许文档范围内进行。
    Set<Long> allowedDocIds = listAccessibleDocumentIds(kbId, userId);

    if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
      allowedDocIds.retainAll(new HashSet<>(request.getDocumentIds()));
    }
    if (allowedDocIds.isEmpty()) {
      return List.of();
    }

    String query = request.getQuery() == null ? "" : request.getQuery().trim();
    List<String> queryTerms = tokenizeQuery(query);
    List<Double> queryVector = aiClient.embedding(query);
    int topK = request.getTopK() == null || request.getTopK() < 1 ? 5 : request.getTopK();
    int candidateTopK = request.getCandidateTopK() == null || request.getCandidateTopK() < topK
        ? Math.max(topK * 4, topK)
        : request.getCandidateTopK();
    double minScore = request.getMinScore() == null ? 0.0 : request.getMinScore();

    List<KnowledgeChunk> allChunks = chunkRepository.findByKnowledgeBaseId(kbId).stream()
        .filter(chunk -> chunk.getKnowledgeDocument() != null
            && allowedDocIds.contains(chunk.getKnowledgeDocument().getId()))
        .collect(Collectors.toList());
    List<KnowledgeChunk> sourceChunks = applyRetryPrefilterIfNeeded(
        allChunks, query, queryTerms, minScore, Boolean.TRUE.equals(request.getRerank()));

    List<KnowledgeSearchResult> candidates = sourceChunks.stream()
        .map(chunk -> {
          List<Double> vec = VectorUtil.fromJson(chunk.getEmbeddingJson());
          double vectorScore = VectorUtil.cosine(queryVector, vec);
          double lexicalScore = lexicalScore(query, queryTerms, chunk.getContent());
          // 混合打分：向量召回 + 关键词匹配，降低因 embedding 异常导致的漏召回
          double score = (vectorScore * 0.8) + (lexicalScore * 0.2);
          return KnowledgeSearchResult.builder()
              .chunkId(chunk.getId())
              .documentId(chunk.getKnowledgeDocument().getId())
              .content(chunk.getContent())
              .score(score)
              .build();
        })
        .filter(r -> r.getScore() >= minScore)
        .sorted(Comparator.comparingDouble(KnowledgeSearchResult::getScore).reversed())
        .limit(candidateTopK)
        .collect(Collectors.toList());

    if (Boolean.TRUE.equals(request.getRerank())) {
      // 重排是增强能力，不影响主流程可用性。
      candidates = rerankByAi(request.getQuery(), candidates);
    }

    List<KnowledgeSearchResult> finalResult = candidates.stream().limit(topK).collect(Collectors.toList());
    if (!finalResult.isEmpty()) {
      return finalResult;
    }

    // 兜底：纯关键词召回（不使用 minScore），避免“知识库明明有内容却返回空”
    return sourceChunks.stream()
        .filter(chunk -> chunk.getKnowledgeDocument() != null
            && allowedDocIds.contains(chunk.getKnowledgeDocument().getId()))
        .map(chunk -> KnowledgeSearchResult.builder()
            .chunkId(chunk.getId())
            .documentId(chunk.getKnowledgeDocument().getId())
            .content(chunk.getContent())
            .score(lexicalScore(query, queryTerms, chunk.getContent()))
            .build())
        .filter(r -> r.getScore() > 0)
        .sorted(Comparator.comparingDouble(KnowledgeSearchResult::getScore).reversed())
        .limit(topK)
        .collect(Collectors.toList());
  }

  /**
   * 仅在“降阈值重试”场景做关键词预筛，避免二次检索再做一次全量向量计算。
   */
  private List<KnowledgeChunk> applyRetryPrefilterIfNeeded(List<KnowledgeChunk> chunks, String query,
      List<String> queryTerms, double minScore, boolean rerank) {
    if (chunks.isEmpty()) {
      return chunks;
    }
    // 只在低阈值 + 关闭重排（即兜底重试）时启用预筛
    if (minScore > 0.0001 || rerank) {
      return chunks;
    }
    String q = query == null ? "" : query.toLowerCase();
    List<KnowledgeChunk> filtered = chunks.stream()
        .filter(chunk -> {
          String c = chunk.getContent() == null ? "" : chunk.getContent().toLowerCase();
          if (!q.isBlank() && c.contains(q)) {
            return true;
          }
          for (String term : queryTerms) {
            if (c.contains(term)) {
              return true;
            }
          }
          return false;
        })
        .limit(600) // 控制重试阶段计算规模
        .collect(Collectors.toList());
    return filtered.isEmpty() ? chunks : filtered;
  }

  private List<String> tokenizeQuery(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    return TOKEN_SPLIT.splitAsStream(query.toLowerCase())
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .filter(s -> s.length() >= 2)
        .distinct()
        .limit(24)
        .collect(Collectors.toList());
  }

  private double lexicalScore(String query, List<String> queryTerms, String content) {
    if (content == null || content.isBlank()) {
      return 0D;
    }
    String low = content.toLowerCase();
    double score = 0D;
    if (query != null && !query.isBlank() && low.contains(query.toLowerCase())) {
      score += 1.0;
    }
    for (String term : queryTerms) {
      if (low.contains(term)) {
        score += 0.25;
      }
    }
    return Math.min(score, 2.5D);
  }

  /**
   * 构建并返回知识图谱数据。
   * <p>
   * 使用轻量级图谱模型（MySQL实现）：
   * 节点类型：DOCUMENT（文档）, DOMAIN_ENTITY（领域实体）, KEYWORD（关键词）。
   * 边类型：
   * - contains (文档 -> 关键词/实体)
   * - mentions (文档 -> 实体)
   * - cooccur (关键词共现)
   * - 实体间关系 (如 '监测', '部署于', '导致' 等，基于规则提取)
   *
   * @param kbId 知识库ID
   * @return 知识图谱结构（点和边）
   */
  public KnowledgeGraphResponse graph(Long kbId) {
    Long userId = currentUserService.getCurrentUserId();
    List<KnowledgeDocument> docs = documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId).stream()
        .filter(doc -> hasReadAccess(doc, userId))
        .limit(30)
        .collect(Collectors.toList());

    // 轻量级图谱模型（MySQL模式）：节点包含 DOCUMENT + DOMAIN_ENTITY + KEYWORD 三类
    Map<String, KnowledgeGraphNode> nodes = new LinkedHashMap<>();
    Map<String, KnowledgeGraphEdge> edges = new LinkedHashMap<>();
    Map<String, Integer> keywordWeight = new HashMap<>();
    Map<String, Integer> entityWeight = new HashMap<>();
    Map<String, Set<String>> effectiveLexicon = buildEffectiveLexicon(kbId);

    for (KnowledgeDocument doc : docs) {
      String docNodeId = "doc-" + doc.getId();
      nodes.put(docNodeId, KnowledgeGraphNode.builder()
          .id(docNodeId)
          .name(doc.getTitle())
          .category("DOCUMENT")
          .value(20)
          .build());

      // 关键词节点用于补充“通用语义线索”，避免图谱只剩领域词典实体。
      Map<String, Integer> keywordFreq = extractKeywordFrequency(doc.getContent(), 6);
      List<String> docKeywords = new ArrayList<>(keywordFreq.keySet());
      for (Map.Entry<String, Integer> kw : keywordFreq.entrySet()) {
        String keyword = kw.getKey();
        int freq = kw.getValue();
        keywordWeight.put(keyword, keywordWeight.getOrDefault(keyword, 0) + freq);
        String kwNodeId = "kw-" + keyword;
        nodes.putIfAbsent(kwNodeId, KnowledgeGraphNode.builder()
            .id(kwNodeId)
            .name(keyword)
            .category("KEYWORD")
            .value(Math.min(60, Math.max(8, freq * 2)))
            .build());

        String edgeKey = docNodeId + "->" + kwNodeId;
        edges.put(edgeKey, KnowledgeGraphEdge.builder()
            .source(docNodeId)
            .target(kwNodeId)
            .label("contains")
            .value(freq)
            .build());
      }

      // 领域实体节点：承载业务语义主干（设备/故障/原因/处置等）。
      Map<String, Integer> entities = extractDomainEntityFrequency(doc.getContent(), effectiveLexicon);
      for (Map.Entry<String, Integer> e : entities.entrySet()) {
        String entityKey = e.getKey();
        int freq = e.getValue();
        entityWeight.put(entityKey, entityWeight.getOrDefault(entityKey, 0) + freq);
        String entityNodeId = "ent-" + entityKey;
        nodes.putIfAbsent(entityNodeId, KnowledgeGraphNode.builder()
            .id(entityNodeId)
            .name(readableEntityName(entityKey))
            .category("DOMAIN_ENTITY")
            .value(Math.min(80, Math.max(12, freq * 3)))
            .build());
        edges.put(docNodeId + "->" + entityNodeId, KnowledgeGraphEdge.builder()
            .source(docNodeId)
            .target(entityNodeId)
            .label("mentions")
            .value(freq)
            .build());
      }

      // 关系边：句子级共现 + 类型规则，优先保证“稳定、可解释”。
      Map<String, Integer> relationFreq = extractEntityRelations(doc.getContent(), entities.keySet());
      for (Map.Entry<String, Integer> relation : relationFreq.entrySet()) {
        String[] parts = relation.getKey().split("\\|", 3);
        if (parts.length < 3) {
          continue;
        }
        String sourceNodeId = "ent-" + parts[0];
        String targetNodeId = "ent-" + parts[1];
        String label = parts[2];
        String edgeKey = sourceNodeId + "->" + targetNodeId + "#" + label;
        KnowledgeGraphEdge old = edges.get(edgeKey);
        int next = old == null ? relation.getValue() : old.getValue() + relation.getValue();
        edges.put(edgeKey, KnowledgeGraphEdge.builder()
            .source(sourceNodeId)
            .target(targetNodeId)
            .label(label)
            .value(next)
            .build());
      }

      for (int i = 0; i < docKeywords.size(); i++) {
        for (int j = i + 1; j < docKeywords.size(); j++) {
          String a = "kw-" + docKeywords.get(i);
          String b = "kw-" + docKeywords.get(j);
          String edgeKey = a.compareTo(b) < 0 ? a + "->" + b : b + "->" + a;
          KnowledgeGraphEdge old = edges.get(edgeKey);
          int next = old == null ? 1 : old.getValue() + 1;
          edges.put(edgeKey, KnowledgeGraphEdge.builder()
              .source(a.compareTo(b) < 0 ? a : b)
              .target(a.compareTo(b) < 0 ? b : a)
              .label("cooccur")
              .value(next)
              .build());
        }
      }
    }

    for (Map.Entry<String, KnowledgeGraphNode> e : nodes.entrySet()) {
      if ("KEYWORD".equals(e.getValue().getCategory())) {
        String keyword = e.getValue().getName();
        int v = keywordWeight.getOrDefault(keyword, 1);
        e.setValue(KnowledgeGraphNode.builder()
            .id(e.getValue().getId())
            .name(keyword)
            .category("KEYWORD")
            .value(Math.min(90, Math.max(10, v * 2)))
            .build());
      } else if ("DOMAIN_ENTITY".equals(e.getValue().getCategory())) {
        String entityKey = e.getValue().getId().replaceFirst("^ent-", "");
        int v = entityWeight.getOrDefault(entityKey, 1);
        e.setValue(KnowledgeGraphNode.builder()
            .id(e.getValue().getId())
            .name(e.getValue().getName())
            .category("DOMAIN_ENTITY")
            .value(Math.min(95, Math.max(14, v * 3)))
            .build());
      }
    }

    return KnowledgeGraphResponse.builder()
        .nodes(new ArrayList<>(nodes.values()))
        .edges(new ArrayList<>(edges.values()))
        .build();
  }

  /**
   * 查询知识图谱中指定节点的详情信息。
   * <p>
   * 根据 nodeId 前缀判断节点类型：
   * - "doc-" 开头：文档节点，返回摘要、关键词和前 8 个分块。
   * - "kw-" 开头：关键词节点，返回该关键词命中的文档和分块。
   * - "ent-" 开头：领域实体节点，返回该实体出现的文档和分块。
   *
   * @param kbId   知识库ID
   * @param nodeId 节点ID（如 "doc-1", "kw-cems", "ent-FAULT|漂移"）
   * @return 节点详情响应
   * @throws EntityNotFoundException  如果节点对应的数据不存在
   * @throws IllegalArgumentException 如果节点ID格式不正确
   */
  public KnowledgeGraphNodeDetailResponse graphNodeDetail(Long kbId, String nodeId) {
    Long userId = currentUserService.getCurrentUserId();
    List<KnowledgeDocument> docs = documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId).stream()
        .filter(doc -> hasReadAccess(doc, userId))
        .collect(Collectors.toList());
    if (docs.isEmpty()) {
      throw new EntityNotFoundException();
    }
    Map<Long, KnowledgeDocument> docById = docs.stream()
        .collect(Collectors.toMap(KnowledgeDocument::getId, d -> d));
    Set<Long> docIds = docById.keySet();

    if (nodeId.startsWith("doc-")) {
      long docId = Long.parseLong(nodeId.substring(4));
      KnowledgeDocument doc = docById.get(docId);
      if (doc == null) {
        throw new EntityNotFoundException();
      }
      List<String> keywords = new ArrayList<>(extractKeywordFrequency(doc.getContent(), 12).keySet());
      List<KnowledgeGraphDetailChunk> chunks = chunkRepository.findByKnowledgeBaseId(kbId).stream()
          .filter(c -> c.getKnowledgeDocument() != null && c.getKnowledgeDocument().getId().equals(docId))
          .sorted(Comparator.comparing(KnowledgeChunk::getChunkIndex))
          .limit(8)
          .map(c -> KnowledgeGraphDetailChunk.builder()
              .chunkId(c.getId())
              .documentId(docId)
              .documentTitle(doc.getTitle())
              .chunkIndex(c.getChunkIndex())
              .snippet(snippet(c.getContent(), 180))
              .hitCount(null)
              .build())
          .collect(Collectors.toList());

      return KnowledgeGraphNodeDetailResponse.builder()
          .nodeId(nodeId)
          .nodeType("DOCUMENT")
          .name(doc.getTitle())
          .summary(snippet(doc.getContent(), 300))
          .relatedDocuments(List.of(KnowledgeGraphDetailDocument.builder()
              .documentId(docId)
              .title(doc.getTitle())
              .hitCount(null)
              .build()))
          .relatedKeywords(keywords)
          .relatedChunks(chunks)
          .build();
    }

    if (nodeId.startsWith("kw-")) {
      String keyword = nodeId.substring(3).trim();
      if (keyword.isBlank()) {
        throw new IllegalArgumentException("Invalid keyword node");
      }

      List<KnowledgeGraphDetailDocument> relatedDocs = docs.stream()
          .map(doc -> {
            int hit = countTokenHit(doc.getContent(), keyword);
            return KnowledgeGraphDetailDocument.builder()
                .documentId(doc.getId())
                .title(doc.getTitle())
                .hitCount(hit)
                .build();
          })
          .filter(d -> d.getHitCount() != null && d.getHitCount() > 0)
          .sorted((a, b) -> Integer.compare(b.getHitCount(), a.getHitCount()))
          .limit(10)
          .collect(Collectors.toList());

      List<KnowledgeGraphDetailChunk> relatedChunks = chunkRepository.findByKnowledgeBaseId(kbId).stream()
          .filter(c -> c.getKnowledgeDocument() != null && docIds.contains(c.getKnowledgeDocument().getId()))
          .map(c -> {
            int hit = countTokenHit(c.getContent(), keyword);
            Long documentId = c.getKnowledgeDocument().getId();
            KnowledgeDocument d = docById.get(documentId);
            return KnowledgeGraphDetailChunk.builder()
                .chunkId(c.getId())
                .documentId(documentId)
                .documentTitle(d == null ? "未知文档" : d.getTitle())
                .chunkIndex(c.getChunkIndex())
                .snippet(snippet(c.getContent(), 180))
                .hitCount(hit)
                .build();
          })
          .filter(c -> c.getHitCount() != null && c.getHitCount() > 0)
          .sorted((a, b) -> Integer.compare(b.getHitCount(), a.getHitCount()))
          .limit(10)
          .collect(Collectors.toList());

      Set<Long> relatedDocIds = relatedDocs.stream().map(KnowledgeGraphDetailDocument::getDocumentId)
          .collect(Collectors.toSet());
      Map<String, Integer> kw = new HashMap<>();
      for (KnowledgeDocument d : docs) {
        if (!relatedDocIds.contains(d.getId())) {
          continue;
        }
        for (Map.Entry<String, Integer> e : extractKeywordFrequency(d.getContent(), 20).entrySet()) {
          if (!e.getKey().equals(keyword)) {
            kw.put(e.getKey(), kw.getOrDefault(e.getKey(), 0) + e.getValue());
          }
        }
      }
      List<String> relatedKeywords = kw.entrySet().stream()
          .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
          .limit(12)
          .map(Map.Entry::getKey)
          .collect(Collectors.toList());

      return KnowledgeGraphNodeDetailResponse.builder()
          .nodeId(nodeId)
          .nodeType("KEYWORD")
          .name(keyword)
          .summary("该关键词在 " + relatedDocs.size() + " 个文档中出现，相关片段 " + relatedChunks.size() + " 条。")
          .relatedDocuments(relatedDocs)
          .relatedKeywords(relatedKeywords)
          .relatedChunks(relatedChunks)
          .build();
    }

    if (nodeId.startsWith("ent-")) {
      String entityKey = nodeId.substring(4).trim();
      if (entityKey.isBlank()) {
        throw new IllegalArgumentException("Invalid entity node");
      }
      String[] parts = entityKey.split("\\|", 2);
      if (parts.length < 2) {
        throw new IllegalArgumentException("Invalid entity node");
      }
      String entityName = parts[1];

      List<KnowledgeGraphDetailDocument> relatedDocs = docs.stream()
          .map(doc -> {
            int hit = countTokenHit(doc.getContent(), entityName);
            return KnowledgeGraphDetailDocument.builder()
                .documentId(doc.getId())
                .title(doc.getTitle())
                .hitCount(hit)
                .build();
          })
          .filter(d -> d.getHitCount() != null && d.getHitCount() > 0)
          .sorted((a, b) -> Integer.compare(b.getHitCount(), a.getHitCount()))
          .limit(10)
          .collect(Collectors.toList());

      List<KnowledgeGraphDetailChunk> relatedChunks = chunkRepository.findByKnowledgeBaseId(kbId).stream()
          .filter(c -> c.getKnowledgeDocument() != null && docIds.contains(c.getKnowledgeDocument().getId()))
          .map(c -> {
            int hit = countTokenHit(c.getContent(), entityName);
            Long documentId = c.getKnowledgeDocument().getId();
            KnowledgeDocument d = docById.get(documentId);
            return KnowledgeGraphDetailChunk.builder()
                .chunkId(c.getId())
                .documentId(documentId)
                .documentTitle(d == null ? "未知文档" : d.getTitle())
                .chunkIndex(c.getChunkIndex())
                .snippet(snippet(c.getContent(), 180))
                .hitCount(hit)
                .build();
          })
          .filter(c -> c.getHitCount() != null && c.getHitCount() > 0)
          .sorted((a, b) -> Integer.compare(b.getHitCount(), a.getHitCount()))
          .limit(10)
          .collect(Collectors.toList());

      Set<Long> relatedDocIds = relatedDocs.stream().map(KnowledgeGraphDetailDocument::getDocumentId)
          .collect(Collectors.toSet());
      Map<String, Integer> relatedKeywordFreq = new HashMap<>();
      for (KnowledgeDocument d : docs) {
        if (!relatedDocIds.contains(d.getId())) {
          continue;
        }
        for (Map.Entry<String, Integer> e : extractKeywordFrequency(d.getContent(), 20).entrySet()) {
          relatedKeywordFreq.put(e.getKey(), relatedKeywordFreq.getOrDefault(e.getKey(), 0) + e.getValue());
        }
      }
      List<String> relatedKeywords = relatedKeywordFreq.entrySet().stream()
          .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
          .limit(12)
          .map(Map.Entry::getKey)
          .collect(Collectors.toList());

      return KnowledgeGraphNodeDetailResponse.builder()
          .nodeId(nodeId)
          .nodeType("DOMAIN_ENTITY")
          .name(readableEntityName(entityKey))
          .summary("该实体在 " + relatedDocs.size() + " 个文档中出现，相关片段 " + relatedChunks.size() + " 条。")
          .relatedDocuments(relatedDocs)
          .relatedKeywords(relatedKeywords)
          .relatedChunks(relatedChunks)
          .build();
    }

    throw new IllegalArgumentException("Unsupported node: " + nodeId);
  }

  /**
   * 使用 LLM 对初检结果进行二次重排（Rerank）。
   * <p>
   * 将候选分块内容和查询词一起发送给AI模型，由模型判断相关性排序。
   * 如果AI调用失败，降级返回原始排序。
   *
   * @param query      用户查询词
   * @param candidates 初检候选结果列表
   * @return 重排后的结果列表
   */
  private List<KnowledgeSearchResult> rerankByAi(String query, List<KnowledgeSearchResult> candidates) {
    if (candidates.size() <= 1) {
      return candidates;
    }
    try {
      String items = candidates.stream()
          .map(c -> "{\"chunkId\":" + c.getChunkId() + ",\"content\":\""
              + c.getContent().replace("\"", "'").replace("\n", " ") + "\"}")
          .collect(Collectors.joining(","));

      String systemPrompt = "你是检索重排助手。根据query与候选内容相关性，返回最相关的chunkId顺序JSON。";
      String userPrompt = "query: " + query + "\n候选: [" + items + "]\n"
          + "仅输出JSON: {\"orderedChunkIds\":[id1,id2,...]}";
      String raw = aiClient.chat(systemPrompt, userPrompt);
      String json = extractJsonObject(raw);
      JsonNode root = MAPPER.readTree(json);

      List<Long> orderedIds = new ArrayList<>();
      for (JsonNode node : root.path("orderedChunkIds")) {
        orderedIds.add(node.asLong());
      }

      Map<Long, KnowledgeSearchResult> byId = new HashMap<>();
      for (KnowledgeSearchResult c : candidates) {
        byId.put(c.getChunkId(), c);
      }

      List<KnowledgeSearchResult> result = new ArrayList<>();
      for (Long id : orderedIds) {
        KnowledgeSearchResult item = byId.remove(id);
        if (item != null) {
          result.add(item);
        }
      }
      result.addAll(byId.values());
      return result;
    } catch (Exception ex) {
      return candidates;
    }
  }

  /**
   * 对文档重新建立索引。
   * <p>
   * 每次内容变更时从零开始重建分块和向量，保证索引的确定性和一致性。
   * 流程：1) 删除旧分块 → 2) 按章节切分新内容 → 3) 紧凑化去重 → 4) 逐块生成向量并持久化。
   *
   * @param doc 待重建索引的知识文档
   */
  private void reindexDocument(KnowledgeDocument doc) {
    // 删除旧索引，避免历史脏块影响召回质量。
    chunkRepository.deleteByKnowledgeDocumentId(doc.getId());
    // 章节感知切分优先保留“同章节语义连贯性”。
    List<String> rawChunks = textChunker.splitBySections(doc.getContent(), 900, 140);
    // 去重/去噪，控制 chunk 数量，避免大文档压垮 embedding 过程。
    List<String> chunks = compactChunks(rawChunks);
    // [AI-READ] 表格双轨：正文分块之外，额外生成结构化表格 JSON 分块，便于精确检索。
    chunks.addAll(extractTableJsonChunks(doc.getContent()));

    // 并行调用 embedding API：各 chunk 之间互相独立，线程池控制并发数。
    @SuppressWarnings("unchecked")
    CompletableFuture<List<Double>>[] futures = new CompletableFuture[chunks.size()];
    for (int i = 0; i < chunks.size(); i++) {
      final String content = chunks.get(i);
      futures[i] = CompletableFuture.supplyAsync(() -> aiClient.embedding(content), embeddingExecutor);
    }
    // 等待全部完成
    CompletableFuture.allOf(futures).join();

    // 按原始顺序收集结果并批量持久化
    for (int i = 0; i < chunks.size(); i++) {
      List<Double> embedding = futures[i].join();
      KnowledgeChunk chunk = KnowledgeChunk.builder()
          .knowledgeBase(doc.getKnowledgeBase())
          .knowledgeDocument(doc)
          .chunkIndex(i + 1)
          .content(chunks.get(i))
          .embeddingJson(VectorUtil.toJson(embedding))
          .embeddingDim(embedding.size())
          .build();
      chunkRepository.save(chunk);
    }
  }

  // [AI-READ] 从纯文本中识别“|”分隔的表格并转为 JSON 分块，保留结构化信息。
  private List<String> extractTableJsonChunks(String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    String[] lines = content.split("\\R");
    List<String> result = new ArrayList<>();
    List<String> currentTableLines = new ArrayList<>();

    for (String line : lines) {
      String normalized = line == null ? "" : line.trim();
      if (isTableLikeLine(normalized)) {
        currentTableLines.add(normalized);
        continue;
      }
      flushTableLines(currentTableLines, result);
    }
    flushTableLines(currentTableLines, result);
    return result;
  }

  private void flushTableLines(List<String> tableLines, List<String> result) {
    if (tableLines.isEmpty()) {
      return;
    }
    String chunk = buildTableJsonChunk(tableLines);
    if (chunk != null && !chunk.isBlank()) {
      result.add(chunk);
    }
    tableLines.clear();
  }

  private boolean isTableLikeLine(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }
    // 至少包含两个“|”才认为是候选表格行，避免把普通句子误判。
    return line.chars().filter(ch -> ch == '|').count() >= 2;
  }

  private String buildTableJsonChunk(List<String> tableLines) {
    List<String[]> rows = new ArrayList<>();
    for (String line : tableLines) {
      String[] cols = splitTableLine(line);
      if (cols.length < 2) {
        continue;
      }
      rows.add(cols);
    }
    if (rows.size() < 2) {
      return null;
    }

    String[] header = rows.get(0);
    List<Map<String, String>> items = new ArrayList<>();
    for (int i = 1; i < rows.size(); i++) {
      String[] row = rows.get(i);
      Map<String, String> obj = new LinkedHashMap<>();
      for (int c = 0; c < header.length; c++) {
        String key = sanitizeCell(header[c], "col" + (c + 1));
        String value = c < row.length ? sanitizeCell(row[c], "") : "";
        obj.put(key, value);
      }
      items.add(obj);
    }
    if (items.isEmpty()) {
      return null;
    }

    Map<String, Object> tableObj = new LinkedHashMap<>();
    tableObj.put("type", "table");
    tableObj.put("columns", header);
    tableObj.put("rows", items);
    String json;
    try {
      json = MAPPER.writeValueAsString(tableObj);
    } catch (Exception ex) {
      return null;
    }

    // 兼顾向量召回：前缀简短自然语言摘要 + JSON 正文。
    return "【结构化表格】列: " + String.join("、", header) + "\n[TABLE_JSON]\n" + json;
  }

  private String[] splitTableLine(String line) {
    String trimmed = line.trim();
    if (trimmed.startsWith("|")) {
      trimmed = trimmed.substring(1);
    }
    if (trimmed.endsWith("|")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return java.util.Arrays.stream(trimmed.split("\\|"))
        .map(cell -> sanitizeCell(cell, ""))
        .toArray(String[]::new);
  }

  private String sanitizeCell(String cell, String fallback) {
    String normalized = cell == null ? "" : cell.replace('\u00A0', ' ').trim();
    return normalized.isBlank() ? fallback : normalized;
  }

  /**
   * 对原始分块进行紧凑化处理。
   * <p>
   * 操作：
   * 1. 压缩连续空白和过长标点序列。
   * 2. 过滤长度不足 40 字符的过短分块。
   * 3. 基于内容去重（忽略大小写）。
   * 4. 最多保留 1200 个分块，防止超大文档导致资源耗尽。
   *
   * @param rawChunks 原始分块列表
   * @return 紧凑化后的分块列表
   */
  private List<String> compactChunks(List<String> rawChunks) {
    if (rawChunks == null || rawChunks.isEmpty()) {
      return List.of();
    }
    Set<String> dedup = new HashSet<>();
    List<String> result = new ArrayList<>();
    for (String chunk : rawChunks) {
      if (chunk == null) {
        continue;
      }
      String normalized = chunk
          .replaceAll("\\s+", " ")
          .replaceAll("[\\p{Punct}]{3,}", " ")
          .trim();
      if (normalized.length() < 40) {
        continue;
      }
      String key = normalized.toLowerCase();
      if (dedup.add(key)) {
        result.add(normalized);
      }
      if (result.size() >= 1200) {
        break;
      }
    }
    return result;
  }

  /**
   * [AI-READ] 基于规则的领域实体识别（NER）。
   * <p>
   * 使用“基础词典 + 知识库自定义词典”做匹配计数，确保行为可审计、可解释。
   */
  private Map<String, Integer> extractDomainEntityFrequency(String content, Map<String, Set<String>> lexicon) {
    if (content == null || content.isBlank()) {
      return Map.of();
    }
    String lower = content.toLowerCase();
    Map<String, Integer> result = new LinkedHashMap<>();
    for (Map.Entry<String, Set<String>> entry : lexicon.entrySet()) {
      String category = entry.getKey();
      for (String term : entry.getValue()) {
        int hit = countTokenHit(lower, term.toLowerCase());
        if (hit > 0) {
          result.put(category + "|" + term, hit);
        }
      }
    }
    return result.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .limit(20)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  private Map<String, Set<String>> buildEffectiveLexicon(Long kbId) {
    // 仅从数据库动态加载启用词典，不再使用代码内置词典。
    Map<String, Set<String>> merged = new LinkedHashMap<>();
    for (KnowledgeBaseDomainLexicon item : lexiconRepository.findByKnowledgeBaseIdAndEnabledTrue(kbId)) {
      String category = item.getCategory() == null ? "" : item.getCategory().trim().toUpperCase();
      String term = item.getTerm() == null ? "" : item.getTerm().trim();
      if (category.isBlank() || term.isBlank()) {
        continue;
      }
      merged.computeIfAbsent(category, k -> new HashSet<>()).add(term);
    }
    return merged;
  }

  /**
   * 将实体键转换为可读名称。
   * <p>
   * 例如："FAULT|漂移" → "FAULT: 漂移"
   *
   * @param entityKey 实体键（格式：类别|术语）
   * @return 可读的实体名称
   */
  private String readableEntityName(String entityKey) {
    String[] parts = entityKey.split("\\|", 2);
    if (parts.length < 2) {
      return entityKey;
    }
    return parts[0] + ": " + parts[1];
  }

  /**
   * 基于句子级共现关系提取实体间的关系边。
   * <p>
   * 策略：将文档按句子分割，如果同一个句子中出现两个或以上已知实体，
   * 则根据实体类别对组合推断关系标签（如 INSTRUMENT→POLLUTANT 推断为 "监测"）。
   * <p>
   * 返回 Key 格式：sourceEntityKey|targetEntityKey|relationLabel
   *
   * @param content    文档内容
   * @param entityKeys 已识别的实体键集合
   * @return 关系键及其出现频率的映射
   */
  private Map<String, Integer> extractEntityRelations(String content, Set<String> entityKeys) {
    if (content == null || content.isBlank() || entityKeys == null || entityKeys.isEmpty()) {
      return Map.of();
    }
    Map<String, Integer> relationFreq = new HashMap<>();
    String[] sentences = content.replace("\r", "\n").split("[。；;！？!?\\n]");
    for (String sentence : sentences) {
      String s = sentence == null ? "" : sentence.trim().toLowerCase();
      if (s.length() < 8) {
        continue;
      }
      List<String> hitEntities = new ArrayList<>();
      for (String entityKey : entityKeys) {
        String[] parts = entityKey.split("\\|", 2);
        if (parts.length < 2) {
          continue;
        }
        String term = parts[1].toLowerCase();
        if (s.contains(term)) {
          hitEntities.add(entityKey);
        }
      }
      if (hitEntities.size() < 2) {
        continue;
      }
      for (int i = 0; i < hitEntities.size(); i++) {
        for (int j = 0; j < hitEntities.size(); j++) {
          if (i == j) {
            continue;
          }
          String source = hitEntities.get(i);
          String target = hitEntities.get(j);
          String label = relationLabel(source, target);
          if (label == null) {
            continue;
          }
          String key = source + "|" + target + "|" + label;
          relationFreq.put(key, relationFreq.getOrDefault(key, 0) + 1);
        }
      }
    }
    return relationFreq;
  }

  /**
   * 根据源实体和目标实体的类别，推断它们之间的关系标签。
   * <p>
   * 映射规则（硬编码领域知识）：
   * - INSTRUMENT → POLLUTANT: "监测"
   * - INSTRUMENT → SITE: "部署于"
   * - FAULT → COMPONENT: "发生于"
   * - FAULT → CAUSE: "由...导致"
   * - ACTION → FAULT: "处置"
   * - PROCESS → STANDARD: "遵循"
   * - ALARM → FAULT: "指向"
   * - EVIDENCE → FAULT: "证明"
   * - PARAMETER → INSTRUMENT: "配置于"
   *
   * @param sourceEntityKey 源实体键
   * @param targetEntityKey 目标实体键
   * @return 关系标签，如果没有匹配规则返回 null
   */
  private String relationLabel(String sourceEntityKey, String targetEntityKey) {
    String sourceType = entityType(sourceEntityKey);
    String targetType = entityType(targetEntityKey);
    if (sourceType == null || targetType == null) {
      return null;
    }
    if ("INSTRUMENT".equals(sourceType) && "POLLUTANT".equals(targetType)) {
      return "监测";
    }
    if ("INSTRUMENT".equals(sourceType) && "SITE".equals(targetType)) {
      return "部署于";
    }
    if ("FAULT".equals(sourceType) && "COMPONENT".equals(targetType)) {
      return "发生于";
    }
    if ("FAULT".equals(sourceType) && "CAUSE".equals(targetType)) {
      return "由...导致";
    }
    if ("ACTION".equals(sourceType) && "FAULT".equals(targetType)) {
      return "处置";
    }
    if ("PROCESS".equals(sourceType) && "STANDARD".equals(targetType)) {
      return "遵循";
    }
    if ("ALARM".equals(sourceType) && "FAULT".equals(targetType)) {
      return "指向";
    }
    if ("EVIDENCE".equals(sourceType) && "FAULT".equals(targetType)) {
      return "证明";
    }
    if ("PARAMETER".equals(sourceType) && "INSTRUMENT".equals(targetType)) {
      return "配置于";
    }
    return null;
  }

  /**
   * 从实体键中提取实体类别。
   * <p>
   * 例如："FAULT|漂移" → "FAULT"
   *
   * @param entityKey 实体键
   * @return 实体类别
   */
  private String entityType(String entityKey) {
    if (entityKey == null) {
      return null;
    }
    String[] parts = entityKey.split("\\|", 2);
    return parts.length < 2 ? null : parts[0];
  }

  /**
   * 从文档内容中提取高频关键词。
   * <p>
   * 处理流程：
   * 1. 截取前 6000 字符（避免大文档耗时过长）。
   * 2. 使用正则按非汉字/字母/数字进行分词。
   * 3. 过滤停用词、过短/过长词、纯数字、字母数字混合词等噪声。
   * 4. 按词频降序排列，取前 N 个。
   *
   * @param content 文档或分块内容
   * @param limit   返回的最大关键词数量
   * @return 关键词及其出现频率的有序映射
   */
  private Map<String, Integer> extractKeywordFrequency(String content, int limit) {
    if (content == null || content.isBlank()) {
      return Map.of();
    }
    String input = content.length() > 6000 ? content.substring(0, 6000) : content;
    Map<String, Integer> freq = new HashMap<>();
    for (String token : TOKEN_SPLIT.split(input)) {
      String t = token.trim().toLowerCase();
      if (t.isBlank()) {
        continue;
      }
      if (t.length() < 2 || t.length() > 24) {
        continue;
      }
      if (t.matches("^\\d+$")) {
        continue;
      }
      if (t.matches("^\\d+[a-z]+$") || t.matches("^[a-z]+\\d+$")) {
        continue;
      }
      if (t.length() <= 2 && t.matches("^[a-z]+$")) {
        continue;
      }
      if (STOPWORDS.contains(t)) {
        continue;
      }
      freq.put(t, freq.getOrDefault(t, 0) + 1);
    }
    return freq.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .limit(limit)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  /**
   * 截取文本摘要，超过最大长度时在末尾添加 "..."。
   *
   * @param text   原始文本
   * @param maxLen 最大长度
   * @return 截取后的摘要
   */
  private String snippet(String text, int maxLen) {
    if (text == null || text.isBlank()) {
      return "";
    }
    String s = text.replaceAll("\\s+", " ").trim();
    if (s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen) + "...";
  }

  /**
   * 统计指定词汇在内容中出现的次数（大小写不敏感）。
   *
   * @param content 文本内容
   * @param token   待匹配的词汇
   * @return 命中次数
   */
  private int countTokenHit(String content, String token) {
    if (content == null || token == null || token.isBlank()) {
      return 0;
    }
    String full = content.toLowerCase();
    String key = token.toLowerCase();
    int count = 0;
    int idx = 0;
    while ((idx = full.indexOf(key, idx)) >= 0) {
      count++;
      idx += key.length();
    }
    return count;
  }

  /**
   * 获取指定知识库中当前用户有权访问的所有文档ID集合。
   *
   * @param kbId   知识库ID
   * @param userId 当前用户ID
   * @return 可访问的文档ID集合
   */
  private Set<Long> listAccessibleDocumentIds(Long kbId, Long userId) {
    List<KnowledgeDocument> docs = documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId);
    return docs.stream()
        .filter(doc -> hasReadAccess(doc, userId))
        .map(KnowledgeDocument::getId)
        .collect(Collectors.toSet());
  }

  /**
   * 判断指定用户是否对某文档有读取权限。
   * <p>
   * 权限判定优先级：
   * 1. 公开文档 (PUBLIC) → 所有人可读
   * 2. 文档创建者 → 可读
   * 3. 被授权用户 → 可读
   *
   * @param doc    知识文档
   * @param userId 用户ID
   * @return 是否有读取权限
   */
  private boolean hasReadAccess(KnowledgeDocument doc, Long userId) {
    if (doc.getVisibility() == KnowledgeVisibility.PUBLIC) {
      return true;
    }
    if (userId == null) {
      return false;
    }
    if (userId.equals(doc.getCreatedBy())) {
      return true;
    }
    return permissionRepository.findByKnowledgeDocumentIdAndUserId(doc.getId(), userId).isPresent();
  }

  /**
   * 断言当前用户是文档的创建者（所有者）。
   *
   * @param doc 知识文档
   * @throws IllegalStateException 如果当前用户不是文档所有者
   */
  private void assertDocumentOwner(KnowledgeDocument doc) {
    Long userId = currentUserService.getCurrentUserId();
    if (userId == null || !userId.equals(doc.getCreatedBy())) {
      throw new IllegalStateException("Only document owner can manage permissions");
    }
  }

  /**
   * 获取指定知识库中的文档，并校验文档是否属于该知识库。
   *
   * @param kbId       知识库ID
   * @param documentId 文档ID
   * @return 知识文档
   * @throws EntityNotFoundException  如果文档不存在
   * @throws IllegalArgumentException 如果文档不属于该知识库
   */
  private KnowledgeDocument getDocumentInKb(Long kbId, Long documentId) {
    KnowledgeDocument doc = documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);
    if (!doc.getKnowledgeBase().getId().equals(kbId)) {
      throw new IllegalArgumentException("Document does not belong to knowledge base");
    }
    return doc;
  }

  /**
   * 从 AI 返回的原始文本中提取 JSON 对象字符串。
   * <p>
   * AI 可能在 JSON 前后包含额外文字说明，此方法截取第一个 '{' 到最后一个 '}' 之间的内容。
   *
   * @param raw AI 返回的原始文本
   * @return 提取出的 JSON 字符串
   */
  private String extractJsonObject(String raw) {
    int first = raw.indexOf('{');
    int last = raw.lastIndexOf('}');
    if (first >= 0 && last > first) {
      return raw.substring(first, last + 1);
    }
    return raw;
  }

  private String storeUploadedFile(Long kbId, MultipartFile file) {
    String original = file.getOriginalFilename() == null ? "unknown.bin" : file.getOriginalFilename();
    String safeName = original.replaceAll("[^A-Za-z0-9._-]", "_");
    Path dir = Path.of(knowledgeStorageDir, "kb-" + kbId);
    Path target = dir.resolve(System.currentTimeMillis() + "_" + safeName);
    try {
      Files.createDirectories(dir);
      try (InputStream in = file.getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return target.toAbsolutePath().toString();
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to store uploaded file", ex);
    }
  }

  private String extractTextFromStoredFile(KnowledgeDocument doc) {
    try {
      byte[] bytes = Files.readAllBytes(Path.of(doc.getStoragePath()));
      String fileName = doc.getFileName() == null ? "" : doc.getFileName().toLowerCase();
      if (fileName.endsWith(".docx")) {
        return extractDocxWithStoredImageMarkers(doc, bytes);
      }
      return textExtractor.extract(bytes, doc.getFileName());
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to re-read stored file: " + doc.getStoragePath(), ex);
    }
  }

  private String extractDocxWithStoredImageMarkers(KnowledgeDocument doc, byte[] bytes) {
    DocumentTextExtractor.DocxExtractionResult result = textExtractor.extractDocxWithImageMarkers(bytes);
    String content = result.getTextWithMarkers();
    for (DocumentTextExtractor.ExtractedImage image : result.getImages()) {
      String imageUrl = storeKnowledgeImage(doc, image);
      String caption = sanitizeCaption(image.getFileName());
      String marker = "[img caption=\"" + caption + "\"]" + imageUrl;
      content = content.replace(image.getMarker(), marker);
    }
    return content;
  }

  private String storeKnowledgeImage(KnowledgeDocument doc, DocumentTextExtractor.ExtractedImage image) {
    String ext = image.getExtension() == null || image.getExtension().isBlank() ? "png" : image.getExtension().toLowerCase();
    LocalDate now = LocalDate.now();
    String dateDir = now.format(DateTimeFormatter.BASIC_ISO_DATE);
    String filename = "kb" + doc.getKnowledgeBase().getId() + "_doc" + doc.getId() + "_img"
        + image.getIndex() + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
    Path dir = Path.of(uploadStorageDir, "knowledge-images", dateDir);
    Path target = dir.resolve(filename);
    try {
      Files.createDirectories(dir);
      Files.write(target, image.getData());
      return "/files/knowledge-images/" + dateDir + "/" + filename;
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to store extracted image for knowledge doc: " + doc.getId(), ex);
    }
  }

  private String sanitizeCaption(String raw) {
    if (raw == null || raw.isBlank()) {
      return "图片";
    }
    return raw.replace("\"", "'").replaceAll("\\s+", " ").trim();
  }

  /** 将知识库实体转换为响应 DTO。 */
  private KnowledgeBaseResponse toBaseResponse(KnowledgeBase base) {
    return KnowledgeBaseResponse.builder()
        .id(base.getId())
        .name(base.getName())
        .description(base.getDescription())
        .createdAt(base.getCreatedAt())
        .build();
  }

  /** 将知识文档实体转换为响应 DTO。 */
  private KnowledgeDocumentResponse toDocumentResponse(KnowledgeDocument doc) {
    return KnowledgeDocumentResponse.builder()
        .id(doc.getId())
        .knowledgeBaseId(doc.getKnowledgeBase().getId())
        .title(doc.getTitle())
        .sourceType(doc.getSourceType())
        .fileName(doc.getFileName())
        .fileType(doc.getFileType())
        .storagePath(doc.getStoragePath())
        .visibility(doc.getVisibility().name())
        .createdAt(doc.getCreatedAt())
        .build();
  }

  /** 将知识分块实体转换为响应 DTO。 */
  private KnowledgeChunkResponse toChunkResponse(KnowledgeChunk chunk) {
    return KnowledgeChunkResponse.builder()
        .id(chunk.getId())
        .knowledgeBaseId(chunk.getKnowledgeBase().getId())
        .knowledgeDocumentId(chunk.getKnowledgeDocument() == null ? null : chunk.getKnowledgeDocument().getId())
        .chunkIndex(chunk.getChunkIndex())
        .content(chunk.getContent())
        .embeddingDim(chunk.getEmbeddingDim())
        .build();
  }
}
