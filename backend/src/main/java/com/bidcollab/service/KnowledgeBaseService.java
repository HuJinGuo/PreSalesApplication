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
import com.bidcollab.entity.KnowledgeChunk;
import com.bidcollab.entity.KnowledgeChunkTerm;
import com.bidcollab.entity.KnowledgeDocument;
import com.bidcollab.entity.KnowledgeDocumentPermission;
import com.bidcollab.entity.ExamPaper;
import com.bidcollab.enums.KnowledgeVisibility;
import com.bidcollab.repository.ExamPaperRepository;
import com.bidcollab.repository.ExamQuestionRepository;
import com.bidcollab.repository.ExamSubmissionRepository;
import com.bidcollab.repository.KnowledgeBaseRepository;
import com.bidcollab.repository.KnowledgeBaseDomainLexiconRepository;
import com.bidcollab.repository.KnowledgeBaseDictionaryPackRepository;
import com.bidcollab.repository.KnowledgeChunkRepository;
import com.bidcollab.repository.KnowledgeChunkTermRepository;
import com.bidcollab.repository.KnowledgeChunkTermRepository.TermStatRow;
import com.bidcollab.repository.KnowledgeDocumentPermissionRepository;
import com.bidcollab.repository.KnowledgeDocumentRepository;
import com.bidcollab.repository.UserRepository;
import com.bidcollab.util.DocumentTextExtractor;
import com.bidcollab.util.TextChunker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
  private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);
  private static final String INDEX_PENDING = "PENDING";
  private static final String INDEX_RUNNING = "RUNNING";
  private static final String INDEX_SUCCESS = "SUCCESS";
  private static final String INDEX_FAILED = "FAILED";
  private static final String INDEX_CANCELED = "CANCELED";
  private static final Duration INDEX_STALE_TIMEOUT = Duration.ofMinutes(30);
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
  private final KnowledgeBaseDictionaryPackRepository kbDictionaryPackRepository;
  private final KnowledgeDocumentRepository documentRepository;
  private final KnowledgeChunkRepository chunkRepository;
  private final KnowledgeChunkTermRepository chunkTermRepository;
  private final KnowledgeDocumentPermissionRepository permissionRepository;
  private final ExamPaperRepository examPaperRepository;
  private final ExamQuestionRepository examQuestionRepository;
  private final ExamSubmissionRepository examSubmissionRepository;
  private final UserRepository userRepository;
  private final CurrentUserService currentUserService;
  private final DomainLexiconService domainLexiconService;
  private final DocumentTextExtractor textExtractor;
  private final TextChunker textChunker;
  private final AiClient aiClient;
  private final MilvusVectorService milvusVectorService;
  private final String knowledgeStorageDir;
  private final String uploadStorageDir;
  /** Embedding 并行线程池，控制并发数避免压垮 AI API */
  private final ExecutorService embeddingExecutor;
  /** 文档重建索引线程池（文档级异步任务） */
  private final ExecutorService knowledgeReindexExecutor;

  public KnowledgeBaseService(KnowledgeBaseRepository baseRepository,
      KnowledgeBaseDomainLexiconRepository lexiconRepository,
      KnowledgeBaseDictionaryPackRepository kbDictionaryPackRepository,
      KnowledgeDocumentRepository documentRepository,
      KnowledgeChunkRepository chunkRepository,
      KnowledgeChunkTermRepository chunkTermRepository,
      KnowledgeDocumentPermissionRepository permissionRepository,
      ExamPaperRepository examPaperRepository,
      ExamQuestionRepository examQuestionRepository,
      ExamSubmissionRepository examSubmissionRepository,
      UserRepository userRepository,
      CurrentUserService currentUserService,
      DomainLexiconService domainLexiconService,
      DocumentTextExtractor textExtractor,
      TextChunker textChunker,
      AiClient aiClient,
      MilvusVectorService milvusVectorService,
      @Value("${app.knowledge.storage-dir:/tmp/bid-knowledge-files}") String knowledgeStorageDir,
      @Value("${app.upload.storage-dir:/tmp/bid-doc-uploads}") String uploadStorageDir,
      @Qualifier("embeddingExecutor") ExecutorService embeddingExecutor,
      @Qualifier("knowledgeReindexExecutor") ExecutorService knowledgeReindexExecutor) {
    this.baseRepository = baseRepository;
    this.lexiconRepository = lexiconRepository;
    this.kbDictionaryPackRepository = kbDictionaryPackRepository;
    this.documentRepository = documentRepository;
    this.chunkRepository = chunkRepository;
    this.chunkTermRepository = chunkTermRepository;
    this.permissionRepository = permissionRepository;
    this.examPaperRepository = examPaperRepository;
    this.examQuestionRepository = examQuestionRepository;
    this.examSubmissionRepository = examSubmissionRepository;
    this.userRepository = userRepository;
    this.currentUserService = currentUserService;
    this.domainLexiconService = domainLexiconService;
    this.textExtractor = textExtractor;
    this.textChunker = textChunker;
    this.aiClient = aiClient;
    this.milvusVectorService = milvusVectorService;
    this.knowledgeStorageDir = knowledgeStorageDir;
    this.uploadStorageDir = uploadStorageDir;
    this.embeddingExecutor = embeddingExecutor;
    this.knowledgeReindexExecutor = knowledgeReindexExecutor;
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
      enqueueReindexTask(doc.getId(), true);
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

    // 批量删除，避免按文档/试卷逐条删除导致慢 SQL 和长事务。
    // examSubmissionRepository.deleteByKnowledgeBaseId(kbId);
    // examQuestionRepository.deleteByKnowledgeBaseId(kbId);
    // examPaperRepository.deleteByKnowledgeBaseId(kbId);

    permissionRepository.deleteByKnowledgeBaseId(kbId);
    milvusVectorService.deleteByKnowledgeBaseId(kbId);
    documentRepository.deleteByKnowledgeBaseId(kbId);
    chunkTermRepository.deleteByKnowledgeBaseId(kbId);
    chunkRepository.deleteByKnowledgeBaseId(kbId);
    kbDictionaryPackRepository.deleteByKnowledgeBaseId(kbId);
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
        .indexStatus(INDEX_PENDING)
        .indexMessage("已加入重建队列")
        .indexProgress(0)
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    documentRepository.save(doc);
    enqueueReindexTask(doc.getId(), false);
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
        .indexStatus(INDEX_PENDING)
        .indexMessage("上传成功，待解析并向量化")
        .indexProgress(0)
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    documentRepository.save(doc);
    enqueueReindexTask(doc.getId(), true);
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
    List<KnowledgeDocument> docs = listAccessibleDocuments(kbId, userId);
    recoverStaleIndexTasks(docs);
    return docs.stream()
        .map(this::toDocumentResponse)
        .collect(Collectors.toList());
  }

  @Transactional
  public void deleteDocument(Long kbId, Long documentId) {
    KnowledgeDocument doc = getDocumentInKb(kbId, documentId);
    assertDocumentOwner(doc);
    // 先切换 taskId，避免并发中的旧索引任务继续覆盖状态。
    doc.setIndexTaskId(UUID.randomUUID().toString().replace("-", ""));
    doc.setIndexStatus(INDEX_CANCELED);
    doc.setIndexMessage("文档已删除");
    doc.setIndexProgress(0);
    doc.setIndexedAt(Instant.now());
    documentRepository.save(doc);

    permissionRepository.deleteByKnowledgeDocumentId(doc.getId());
    chunkTermRepository.deleteByKnowledgeDocumentId(doc.getId());
    chunkRepository.deleteByKnowledgeDocumentId(doc.getId());
    milvusVectorService.deleteByDocumentId(doc.getId());
    documentRepository.delete(doc);
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
    List<KnowledgeChunk> chunks = chunkRepository.findByKnowledgeBaseId(kbId).stream()
        .filter(chunk -> chunk.getKnowledgeDocument() != null
            && allowedDocIds.contains(chunk.getKnowledgeDocument().getId()))
        .collect(Collectors.toList());
    if (chunks.isEmpty()) {
      return List.of();
    }
    List<Long> chunkIds = chunks.stream().map(KnowledgeChunk::getId).toList();
    Map<Long, List<String>> keywordMap = chunkTermRepository
        .findByKnowledgeBaseIdAndTermTypeAndKnowledgeChunkIdIn(kbId, "KEYWORD", chunkIds).stream()
        .collect(Collectors.groupingBy(
            term -> term.getKnowledgeChunk().getId(),
            Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                .sorted((a, b) -> Integer.compare(
                    b.getFrequency() == null ? 0 : b.getFrequency(),
                    a.getFrequency() == null ? 0 : a.getFrequency()))
                .map(KnowledgeChunkTerm::getTermName)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .limit(12)
                .collect(Collectors.toList()))));
    return chunks.stream()
        .map(chunk -> toChunkResponse(chunk, keywordMap.getOrDefault(chunk.getId(), List.of())))
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
    return searchByUser(kbId, request, currentUserService.getCurrentUserId());
  }

  public List<KnowledgeSearchResult> searchAsUser(Long kbId, KnowledgeSearchRequest request, Long userId) {
    return searchByUser(kbId, request, userId);
  }

  private List<KnowledgeSearchResult> searchByUser(Long kbId, KnowledgeSearchRequest request, Long userId) {
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

    List<MilvusVectorService.SearchHit> milvusHits = milvusVectorService.search(
        kbId, allowedDocIds, queryVector, candidateTopK);
    if (milvusHits.isEmpty()) {
      return List.of();
    }
    Map<Long, Double> scoreByChunkId = milvusHits.stream()
        .collect(Collectors.toMap(
            MilvusVectorService.SearchHit::getChunkId,
            MilvusVectorService.SearchHit::getScore,
            (a, b) -> a,
            LinkedHashMap::new));
    List<Long> chunkIds = new ArrayList<>(scoreByChunkId.keySet());
    Map<Long, KnowledgeChunk> chunkById = chunkRepository.findAllById(chunkIds).stream()
        .filter(chunk -> chunk.getKnowledgeDocument() != null
            && allowedDocIds.contains(chunk.getKnowledgeDocument().getId()))
        .collect(Collectors.toMap(KnowledgeChunk::getId, c -> c));

    List<KnowledgeSearchResult> candidates = chunkIds.stream()
        .map(chunkById::get)
        .filter(c -> c != null)
        .map(chunk -> {
          double vectorScore = scoreByChunkId.getOrDefault(chunk.getId(), 0D);
          double lexicalScore = lexicalScore(query, queryTerms, chunk.getContent());
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

    return List.of();
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
    List<KnowledgeDocument> docs = listAccessibleDocuments(kbId, userId).stream()
        .limit(30)
        .collect(Collectors.toList());
    if (docs.isEmpty()) {
      return KnowledgeGraphResponse.builder().nodes(List.of()).edges(List.of()).build();
    }
    Set<Long> docIds = docs.stream().map(KnowledgeDocument::getId).collect(Collectors.toSet());
    List<TermStatRow> terms = chunkTermRepository.findStatsByKnowledgeBaseIdAndKnowledgeDocumentIdIn(kbId, docIds);
    Map<Long, List<TermStatRow>> termsByDoc = terms.stream()
        .collect(Collectors.groupingBy(TermStatRow::getKnowledgeDocumentId));

    // 轻量级图谱模型（MySQL模式）：节点包含 DOCUMENT + DOMAIN_ENTITY + KEYWORD 三类
    Map<String, KnowledgeGraphNode> nodes = new LinkedHashMap<>();
    Map<String, KnowledgeGraphEdge> edges = new LinkedHashMap<>();
    Map<String, Integer> keywordWeight = new HashMap<>();
    Map<String, Integer> entityWeight = new HashMap<>();

    for (KnowledgeDocument doc : docs) {
      String docNodeId = "doc-" + doc.getId();
      nodes.put(docNodeId, KnowledgeGraphNode.builder()
          .id(docNodeId)
          .name(doc.getTitle())
          .category("DOCUMENT")
          .value(20)
          .build());

      Map<String, Integer> keywordFreq = aggregateTermFrequency(
          termsByDoc.getOrDefault(doc.getId(), List.of()), "KEYWORD", 6);
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

      Map<String, Integer> entities = aggregateTermFrequency(
          termsByDoc.getOrDefault(doc.getId(), List.of()), "DOMAIN_ENTITY", 10);
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

      List<String> entityKeys = new ArrayList<>(entities.keySet());
      for (int i = 0; i < entityKeys.size(); i++) {
        for (int j = i + 1; j < entityKeys.size(); j++) {
          String left = entityKeys.get(i);
          String right = entityKeys.get(j);
          String label = relationLabel(left, right);
          if (label == null) {
            continue;
          }
          String sourceNodeId = "ent-" + left;
          String targetNodeId = "ent-" + right;
          String edgeKey = sourceNodeId + "->" + targetNodeId + "#" + label;
          int strength = Math.min(5, entities.get(left) + entities.get(right));
          KnowledgeGraphEdge old = edges.get(edgeKey);
          int next = old == null ? strength : old.getValue() + strength;
          edges.put(edgeKey, KnowledgeGraphEdge.builder()
              .source(sourceNodeId)
              .target(targetNodeId)
              .label(label)
              .value(next)
              .build());
        }
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
    List<KnowledgeDocument> docs = listAccessibleDocuments(kbId, userId);
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
      List<TermStatRow> docTerms = chunkTermRepository.findStatsByKnowledgeBaseIdAndKnowledgeDocumentIdIn(
          kbId, List.of(docId));
      List<String> keywords = new ArrayList<>(
          aggregateTermFrequency(docTerms, "KEYWORD", 12).keySet());
      List<KnowledgeGraphDetailChunk> chunks = chunkRepository
          .findByKnowledgeBaseIdAndKnowledgeDocumentIdOrderByChunkIndexAsc(kbId, docId).stream()
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

      List<TermStatRow> keywordTerms = chunkTermRepository
          .findStatsByKnowledgeBaseIdAndTermTypeAndTermKeyAndKnowledgeDocumentIdIn(kbId, "KEYWORD", keyword, docIds);
      Map<Long, Integer> docHitMap = new HashMap<>();
      Map<Long, Integer> chunkHitMap = new HashMap<>();
      for (TermStatRow term : keywordTerms) {
        Long documentId = term.getKnowledgeDocumentId();
        Long chunkId = term.getKnowledgeChunkId();
        docHitMap.put(documentId, docHitMap.getOrDefault(documentId, 0) + term.getFrequency());
        chunkHitMap.put(chunkId, chunkHitMap.getOrDefault(chunkId, 0) + term.getFrequency());
      }

      List<KnowledgeGraphDetailDocument> relatedDocs = docs.stream()
          .map(doc -> KnowledgeGraphDetailDocument.builder()
              .documentId(doc.getId())
              .title(doc.getTitle())
              .hitCount(docHitMap.getOrDefault(doc.getId(), 0))
              .build())
          .filter(d -> d.getHitCount() != null && d.getHitCount() > 0)
          .sorted((a, b) -> Integer.compare(b.getHitCount(), a.getHitCount()))
          .limit(10)
          .collect(Collectors.toList());

      Set<Long> relatedChunkIds = chunkHitMap.keySet();
      List<KnowledgeGraphDetailChunk> relatedChunks = (relatedChunkIds.isEmpty() ? List.<KnowledgeChunk>of()
          : chunkRepository.findAllById(relatedChunkIds)).stream()
          .map(c -> {
            Long documentId = c.getKnowledgeDocument().getId();
            KnowledgeDocument d = docById.get(documentId);
            return KnowledgeGraphDetailChunk.builder()
                .chunkId(c.getId())
                .documentId(documentId)
                .documentTitle(d == null ? "未知文档" : d.getTitle())
                .chunkIndex(c.getChunkIndex())
                .snippet(snippet(c.getContent(), 180))
                .hitCount(chunkHitMap.getOrDefault(c.getId(), 0))
                .build();
          })
          .filter(c -> c.getHitCount() != null && c.getHitCount() > 0)
          .sorted((a, b) -> Integer.compare(b.getHitCount(), a.getHitCount()))
          .limit(10)
          .collect(Collectors.toList());

      Set<Long> relatedDocIds = relatedDocs.stream().map(KnowledgeGraphDetailDocument::getDocumentId)
          .collect(Collectors.toSet());
      List<TermStatRow> relatedDocTerms = relatedDocIds.isEmpty() ? List.of()
          : chunkTermRepository.findStatsByKnowledgeBaseIdAndKnowledgeDocumentIdIn(kbId, relatedDocIds);
      Map<Long, List<TermStatRow>> relatedTermsByDoc = relatedDocTerms.stream()
          .collect(Collectors.groupingBy(TermStatRow::getKnowledgeDocumentId));
      Map<String, Integer> kw = new HashMap<>();
      for (KnowledgeDocument d : docs) {
        if (!relatedDocIds.contains(d.getId())) {
          continue;
        }
        for (Map.Entry<String, Integer> e : aggregateTermFrequency(
            relatedTermsByDoc.getOrDefault(d.getId(), List.of()), "KEYWORD", 20).entrySet()) {
          if (!e.getKey().equalsIgnoreCase(keyword)) {
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
      List<TermStatRow> entityTerms = chunkTermRepository
          .findStatsByKnowledgeBaseIdAndTermTypeAndTermKeyAndKnowledgeDocumentIdIn(
              kbId, "DOMAIN_ENTITY", entityKey, docIds);
      Map<Long, Integer> docHitMap = new HashMap<>();
      Map<Long, Integer> chunkHitMap = new HashMap<>();
      for (TermStatRow term : entityTerms) {
        Long documentId = term.getKnowledgeDocumentId();
        Long chunkId = term.getKnowledgeChunkId();
        docHitMap.put(documentId, docHitMap.getOrDefault(documentId, 0) + term.getFrequency());
        chunkHitMap.put(chunkId, chunkHitMap.getOrDefault(chunkId, 0) + term.getFrequency());
      }

      List<KnowledgeGraphDetailDocument> relatedDocs = docs.stream()
          .map(doc -> KnowledgeGraphDetailDocument.builder()
              .documentId(doc.getId())
              .title(doc.getTitle())
              .hitCount(docHitMap.getOrDefault(doc.getId(), 0))
              .build())
          .filter(d -> d.getHitCount() != null && d.getHitCount() > 0)
          .sorted((a, b) -> Integer.compare(b.getHitCount(), a.getHitCount()))
          .limit(10)
          .collect(Collectors.toList());

      Set<Long> relatedChunkIds = chunkHitMap.keySet();
      List<KnowledgeGraphDetailChunk> relatedChunks = (relatedChunkIds.isEmpty() ? List.<KnowledgeChunk>of()
          : chunkRepository.findAllById(relatedChunkIds)).stream()
          .map(c -> {
            Long documentId = c.getKnowledgeDocument().getId();
            KnowledgeDocument d = docById.get(documentId);
            return KnowledgeGraphDetailChunk.builder()
                .chunkId(c.getId())
                .documentId(documentId)
                .documentTitle(d == null ? "未知文档" : d.getTitle())
                .chunkIndex(c.getChunkIndex())
                .snippet(snippet(c.getContent(), 180))
                .hitCount(chunkHitMap.getOrDefault(c.getId(), 0))
                .build();
          })
          .filter(c -> c.getHitCount() != null && c.getHitCount() > 0)
          .sorted((a, b) -> Integer.compare(b.getHitCount(), a.getHitCount()))
          .limit(10)
          .collect(Collectors.toList());

      Set<Long> relatedDocIds = relatedDocs.stream().map(KnowledgeGraphDetailDocument::getDocumentId)
          .collect(Collectors.toSet());
      List<TermStatRow> relatedDocTerms = relatedDocIds.isEmpty() ? List.of()
          : chunkTermRepository.findStatsByKnowledgeBaseIdAndKnowledgeDocumentIdIn(kbId, relatedDocIds);
      Map<Long, List<TermStatRow>> relatedTermsByDoc = relatedDocTerms.stream()
          .collect(Collectors.groupingBy(TermStatRow::getKnowledgeDocumentId));
      Map<String, Integer> relatedKeywordFreq = new HashMap<>();
      for (KnowledgeDocument d : docs) {
        if (!relatedDocIds.contains(d.getId())) {
          continue;
        }
        for (Map.Entry<String, Integer> e : aggregateTermFrequency(
            relatedTermsByDoc.getOrDefault(d.getId(), List.of()), "KEYWORD", 20).entrySet()) {
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
   * 入队一个文档重建任务。
   *
   * @param documentId            文档ID
   * @param refreshFromStoredFile 是否先从落盘文件重新提取文本（上传文档场景）
   */
  private void enqueueReindexTask(Long documentId, boolean refreshFromStoredFile) {
    String taskId = UUID.randomUUID().toString().replace("-", "");
    KnowledgeDocument doc = documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);
    doc.setIndexTaskId(taskId);
    doc.setIndexStatus(INDEX_PENDING);
    doc.setIndexMessage("排队中");
    doc.setIndexProgress(0);
    doc.setIndexedAt(null);
    documentRepository.save(doc);
    Runnable submitTask = () -> safeSubmitReindexTask(documentId, taskId, refreshFromStoredFile);
    // 避免“主事务未提交，子线程先读取”导致任务未真正启动而状态卡在 PENDING。
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          submitTask.run();
        }
      });
    } else {
      submitTask.run();
    }
  }

  /**
   * 安全提交重建任务，避免 afterCommit 中提交失败时文档永久停留在 PENDING。
   */
  private void safeSubmitReindexTask(Long documentId, String taskId, boolean refreshFromStoredFile) {
    try {
      knowledgeReindexExecutor.submit(() -> runReindexTask(documentId, taskId, refreshFromStoredFile));
    } catch (RejectedExecutionException ex) {
      log.error("Reindex task rejected. docId={}, taskId={}", documentId, taskId, ex);
      tryUpdateTaskStatus(documentId, taskId, INDEX_FAILED, "任务入队失败：线程池不可用", 0);
    } catch (Exception ex) {
      log.error("Reindex task submit failed. docId={}, taskId={}", documentId, taskId, ex);
      tryUpdateTaskStatus(documentId, taskId, INDEX_FAILED, "任务入队失败：" + ex.getMessage(), 0);
    }
  }

  /**
   * 执行文档重建任务（异步线程内）。
   * <p>
   * 通过 taskId 防止旧任务覆盖新任务状态。
   */
  private void runReindexTask(Long documentId, String taskId, boolean refreshFromStoredFile) {
    if (!tryUpdateTaskStatus(documentId, taskId, INDEX_RUNNING, "索引重建中", 2)) {
      return;
    }
    try {
      KnowledgeDocument doc = documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);
      if (!taskId.equals(doc.getIndexTaskId())) {
        return;
      }

      if (refreshFromStoredFile
          && "UPLOAD".equalsIgnoreCase(doc.getSourceType())
          && doc.getStoragePath() != null
          && !doc.getStoragePath().isBlank()) {
        doc.setContent(extractTextFromStoredFile(doc));
        documentRepository.save(doc);
      }

      reindexDocument(doc, taskId);
      tryUpdateTaskStatus(documentId, taskId, INDEX_SUCCESS, "索引完成", 100);
    } catch (EntityNotFoundException ignored) {
      // 文档被删除时，后台任务自然退出即可。
    } catch (Exception ex) {
      String message = ex.getMessage() == null ? "索引失败" : ex.getMessage();
      if (message.length() > 900) {
        message = message.substring(0, 900);
      }
      tryUpdateTaskStatus(documentId, taskId, INDEX_FAILED, message, 0);
    }
  }

  /**
   * 清理“卡死”的 PENDING/RUNNING 状态。
   * 场景：服务重启、进程异常退出后，内存队列丢失，但数据库状态还停留在处理中。
   */
  @Transactional
  protected void recoverStaleIndexTasks(List<KnowledgeDocument> docs) {
    Instant now = Instant.now();
    for (KnowledgeDocument doc : docs) {
      String status = doc.getIndexStatus();
      if (!(INDEX_PENDING.equals(status) || INDEX_RUNNING.equals(status))) {
        continue;
      }
      Instant heartbeat = doc.getUpdatedAt() == null ? doc.getCreatedAt() : doc.getUpdatedAt();
      if (heartbeat == null) {
        continue;
      }
      if (Duration.between(heartbeat, now).compareTo(INDEX_STALE_TIMEOUT) < 0) {
        continue;
      }
      doc.setIndexStatus(INDEX_FAILED);
      doc.setIndexProgress(0);
      doc.setIndexedAt(now);
      doc.setIndexMessage("任务超时中断（服务重启或队列丢失），请重试重建索引");
      documentRepository.save(doc);
    }
  }

  /**
   * CAS 风格更新任务状态：仅当前任务仍是最新 taskId 时才更新。
   */
  private boolean tryUpdateTaskStatus(Long documentId, String taskId, String status, String message) {
    return tryUpdateTaskStatus(documentId, taskId, status, message, null);
  }

  private boolean tryUpdateTaskStatus(Long documentId, String taskId, String status, String message, Integer progress) {
    KnowledgeDocument doc = documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);
    if (!taskId.equals(doc.getIndexTaskId())) {
      return false;
    }
    doc.setIndexStatus(status);
    doc.setIndexMessage(message);
    if (progress != null) {
      doc.setIndexProgress(Math.max(0, Math.min(100, progress)));
    }
    if (INDEX_SUCCESS.equals(status) || INDEX_FAILED.equals(status)) {
      doc.setIndexedAt(Instant.now());
    } else {
      doc.setIndexedAt(null);
    }
    documentRepository.save(doc);
    return true;
  }

  /**
   * 对文档重新建立索引。
   * <p>
   * 每次内容变更时从零开始重建分块和向量，保证索引的确定性和一致性。
   * 流程：1) 删除旧分块 → 2) 按章节切分新内容 → 3) 紧凑化去重 → 4) 逐块生成向量并持久化。
   *
   * @param doc 待重建索引的知识文档
   */
  private void reindexDocument(KnowledgeDocument doc, String taskId) {
    tryUpdateTaskStatus(doc.getId(), taskId, INDEX_RUNNING, "删除旧索引", 8);
    // 删除旧索引，避免历史脏块影响召回质量。
    milvusVectorService.deleteByDocumentId(doc.getId());
    chunkTermRepository.deleteByKnowledgeDocumentId(doc.getId());
    chunkRepository.deleteByKnowledgeDocumentId(doc.getId());
    tryUpdateTaskStatus(doc.getId(), taskId, INDEX_RUNNING, "切分文档中", 18);
    // 新策略：chunk = 最后一级标题 + 正文；完整路径进入元数据（sectionPath）。
    List<TextChunker.StructuredChunk> rawChunks = textChunker.splitBySectionsWithMetadata(doc.getContent(), 900, 140);
    // 去重/去噪，控制 chunk 数量，避免大文档压垮 embedding 过程。
    List<TextChunker.StructuredChunk> chunks = compactStructuredChunks(rawChunks);

    // *领域词典处理 */
    Map<String, Map<String, String>> aliasMap = domainLexiconService
        .buildEffectiveLexiconAliasMap(doc.getKnowledgeBase().getId());

    List<String> embeddingChunks = chunks.stream().map(chunk -> normalizeChunkByLexicon(chunk.getContent(), aliasMap)).toList();

    tryUpdateTaskStatus(doc.getId(), taskId, INDEX_RUNNING, "生成向量中", 28);
    // 并行调用 embedding API：各 chunk 之间互相独立，线程池控制并发数。
    @SuppressWarnings("unchecked")
    CompletableFuture<List<Double>>[] futures = new CompletableFuture[chunks.size()];
    for (int i = 0; i < chunks.size(); i++) {
      final String content = embeddingChunks.get(i);
      futures[i] = CompletableFuture.supplyAsync(() -> aiClient.embedding(content), embeddingExecutor);
    }
    // 等待全部完成
    CompletableFuture.allOf(futures).join();
    tryUpdateTaskStatus(doc.getId(), taskId, INDEX_RUNNING, "保存向量中", 56);

    // 按原始顺序收集结果并持久化；同时缓存关键词/实体，供图谱直接读取，避免在线重复抽取。
    List<KnowledgeChunkTerm> terms = new ArrayList<>();
    List<MilvusVectorService.ChunkVectorRecord> vectorRecords = new ArrayList<>();
    int progressStep = Math.max(1, chunks.size() / 12);
    for (int i = 0; i < chunks.size(); i++) {
      List<Double> embedding = futures[i].join();
      TextChunker.StructuredChunk structured = chunks.get(i);
      KnowledgeChunk chunk = KnowledgeChunk.builder()
          .knowledgeBase(doc.getKnowledgeBase())
          .knowledgeDocument(doc)
          .chunkIndex(i + 1)
          .sectionTitle(structured.getSectionTitle())
          .sectionPath(structured.getSectionPath())
          .chunkType(structured.getChunkType())
          .content(structured.getContent())
          .embeddingJson("[]")
          .embeddingDim(embedding.size())
          .build();
      chunkRepository.save(chunk);
      vectorRecords.add(new MilvusVectorService.ChunkVectorRecord(chunk.getId(), chunk.getChunkIndex(), embedding));

      Map<String, Integer> keywordFreq = extractKeywordFrequency(structured.getContent(), 10);
      for (Map.Entry<String, Integer> e : keywordFreq.entrySet()) {
        terms.add(KnowledgeChunkTerm.builder()
            .knowledgeBase(doc.getKnowledgeBase())
            .knowledgeDocument(doc)
            .knowledgeChunk(chunk)
            .termType("KEYWORD")
            .termKey(e.getKey())
            .termName(e.getKey())
            .frequency(e.getValue())
            .build());
      }

      Map<String, Integer> entityFreq = extractDomainEntityFrequency(structured.getContent(), aliasMap);
      for (Map.Entry<String, Integer> e : entityFreq.entrySet()) {
        terms.add(KnowledgeChunkTerm.builder()
            .knowledgeBase(doc.getKnowledgeBase())
            .knowledgeDocument(doc)
            .knowledgeChunk(chunk)
            .termType("DOMAIN_ENTITY")
            .termKey(e.getKey())
            .termName(readableEntityName(e.getKey()))
            .frequency(e.getValue())
            .build());
      }
      if (i % progressStep == 0 || i == chunks.size() - 1) {
        int p = 56 + (int) (((i + 1) * 1.0 / Math.max(chunks.size(), 1)) * 38);
        tryUpdateTaskStatus(doc.getId(), taskId, INDEX_RUNNING, "保存中 " + (i + 1) + "/" + chunks.size(), p);
      }
    }
    if (!terms.isEmpty()) {
      chunkTermRepository.saveAll(terms);
    }
    if (!vectorRecords.isEmpty()) {
      milvusVectorService.upsert(doc.getKnowledgeBase().getId(), doc.getId(), vectorRecords);
    }
    tryUpdateTaskStatus(doc.getId(), taskId, INDEX_RUNNING, "索引收尾中", 98);
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

  private List<TextChunker.StructuredChunk> compactStructuredChunks(List<TextChunker.StructuredChunk> rawChunks) {
    if (rawChunks == null || rawChunks.isEmpty()) {
      return List.of();
    }
    Set<String> dedup = new HashSet<>();
    List<TextChunker.StructuredChunk> result = new ArrayList<>();
    for (TextChunker.StructuredChunk chunk : rawChunks) {
      if (chunk == null || chunk.getContent() == null) {
        continue;
      }
      String normalized = chunk.getContent()
          .replaceAll("\\s+", " ")
          .replaceAll("[\\p{Punct}]{3,}", " ")
          .trim();
      if (normalized.length() < 20) {
        continue;
      }
      String key = ((chunk.getSectionPath() == null ? "" : chunk.getSectionPath()) + "|" + normalized).toLowerCase();
      if (dedup.add(key)) {
        result.add(TextChunker.StructuredChunk.builder()
            .content(normalized)
            .sectionTitle(chunk.getSectionTitle())
            .sectionPath(chunk.getSectionPath())
            .chunkType(chunk.getChunkType())
            .build());
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
  private Map<String, Integer> extractDomainEntityFrequency(String content, Map<String, Map<String, String>> lexicon) {
    if (content == null || content.isBlank()) {
      return Map.of();
    }
    String lower = content.toLowerCase();
    Map<String, Integer> result = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : lexicon.entrySet()) {
      String category = entry.getKey();
      for (Map.Entry<String, String> aliasEntry : entry.getValue().entrySet()) {
        String alias = aliasEntry.getKey();
        String standardTerm = aliasEntry.getValue();
        int hit = countTokenHit(lower, alias.toLowerCase());
        if (hit > 0) {
          String key = category + "|" + standardTerm;
          result.put(key, result.getOrDefault(key, 0) + hit);
        }
      }
    }
    return result.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .limit(20)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  private Map<String, Map<String, String>> buildEffectiveLexicon(Long kbId) {
    // 全局词典包 + 知识库本地词条（含同义词归一）动态合并。
    return domainLexiconService.buildEffectiveLexiconAliasMap(kbId);
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
    Map<String, Integer> aiResult = extractKeywordFrequencyByAi(content, limit);
    if (!aiResult.isEmpty()) {
      return aiResult;
    }
    return extractKeywordFrequencyFallback(content, limit);
  }

  /**
   * AI 优先关键词提取：提取失败或结果无效时返回空，交由规则兜底。
   */
  private Map<String, Integer> extractKeywordFrequencyByAi(String content, int limit) {
    try {
      String raw = aiClient.extractKeywordFrequency(content);
      if (raw == null || raw.isBlank()) {
        return Map.of();
      }
      String json = extractJsonObject(raw.trim());
      JsonNode root = MAPPER.readTree(json);
      if (!root.isObject()) {
        return Map.of();
      }
      Map<String, Integer> freq = new HashMap<>();
      root.fields().forEachRemaining(entry -> {
        String key = normalizeKeyword(entry.getKey());
        if (key == null) {
          return;
        }
        JsonNode valueNode = entry.getValue();
        if (valueNode == null || !valueNode.isNumber()) {
          return;
        }
        int count = Math.max(0, valueNode.asInt());
        if (count <= 0) {
          return;
        }
        freq.put(key, freq.getOrDefault(key, 0) + count);
      });
      return freq.entrySet().stream()
          .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
          .limit(limit)
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    } catch (Exception ignore) {
      return Map.of();
    }
  }

  /**
   * 规则兜底关键词提取（旧方案）。
   */
  private Map<String, Integer> extractKeywordFrequencyFallback(String content, int limit) {
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
   * 关键词归一化与过滤：过滤噪声词，避免脏关键词进入图谱。
   */
  private String normalizeKeyword(String keyword) {
    if (keyword == null) {
      return null;
    }
    String t = keyword.trim().toLowerCase().replaceAll("\\s+", " ");
    if (t.isBlank()) {
      return null;
    }
    if (t.length() < 2 || t.length() > 24) {
      return null;
    }
    if (t.matches("^\\d+$")) {
      return null;
    }
    if (t.matches("^\\d+[a-z]+$") || t.matches("^[a-z]+\\d+$")) {
      return null;
    }
    if (t.length() <= 2 && t.matches("^[a-z]+$")) {
      return null;
    }
    if (STOPWORDS.contains(t)) {
      return null;
    }
    return t;
  }

  private Map<String, Integer> aggregateTermFrequency(List<TermStatRow> terms, String termType, int limit) {
    if (terms == null || terms.isEmpty()) {
      return Map.of();
    }
    Map<String, Integer> freq = new HashMap<>();
    for (TermStatRow term : terms) {
      if (term == null || term.getTermType() == null || term.getTermKey() == null) {
        continue;
      }
      if (!termType.equalsIgnoreCase(term.getTermType())) {
        continue;
      }
      int count = term.getFrequency() == null ? 1 : Math.max(1, term.getFrequency());
      freq.put(term.getTermKey(), freq.getOrDefault(term.getTermKey(), 0) + count);
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
   * 基于领域词典对分块文本做术语归一。
   * <p>
   * 用于 embedding 入参预处理：将别名替换为标准术语。
   *
   * @param text     原始分块文本
   * @param aliasMap 词典映射（category -> alias -> standardTerm）
   * @return 归一化后的文本
   */
  private String normalizeChunkByLexicon(String text, Map<String, Map<String, String>> aliasMap) {
    if (text == null || text.isBlank() || aliasMap == null || aliasMap.isEmpty()) {
      return text;
    }
    String normalized = text;
    for (Map<String, String> categoryMap : aliasMap.values()) {
      if (categoryMap == null || categoryMap.isEmpty()) {
        continue;
      }
      List<Map.Entry<String, String>> entries = categoryMap.entrySet().stream()
          .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
          .toList();
      for (Map.Entry<String, String> entry : entries) {
        String alias = entry.getKey();
        String standard = entry.getValue();
        if (alias == null || standard == null || alias.isBlank() || standard.isBlank() || alias.equals(standard)) {
          continue;
        }
        normalized = normalized.replace(alias, standard);
      }
    }
    return normalized;
  }

  /**
   * 获取指定知识库中当前用户有权访问的所有文档ID集合。
   *
   * @param kbId   知识库ID
   * @param userId 当前用户ID
   * @return 可访问的文档ID集合
   */
  private Set<Long> listAccessibleDocumentIds(Long kbId, Long userId) {
    return listAccessibleDocuments(kbId, userId).stream()
        .map(KnowledgeDocument::getId)
        .collect(Collectors.toSet());
  }

  /**
   * 一次性加载当前用户可访问的文档，避免逐文档权限查询的 N+1 问题。
   *
   * @param kbId   知识库ID
   * @param userId 用户ID
   * @return 可访问文档列表
   */
  private List<KnowledgeDocument> listAccessibleDocuments(Long kbId, Long userId) {
    List<KnowledgeDocument> docs = documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId);
    if (docs.isEmpty()) {
      return List.of();
    }
    if (userId == null) {
      return docs.stream().filter(doc -> doc.getVisibility() == KnowledgeVisibility.PUBLIC).toList();
    }
    Set<Long> grantedDocIds = permissionRepository.findByUserId(userId).stream()
        .map(KnowledgeDocumentPermission::getKnowledgeDocumentId)
        .collect(Collectors.toSet());
    return docs.stream()
        .filter(doc -> doc.getVisibility() == KnowledgeVisibility.PUBLIC
            || userId.equals(doc.getCreatedBy())
            || grantedDocIds.contains(doc.getId()))
        .toList();
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
    String ext = image.getExtension() == null || image.getExtension().isBlank() ? "png"
        : image.getExtension().toLowerCase();
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
    String indexStatus = doc.getIndexStatus() == null || doc.getIndexStatus().isBlank()
        ? INDEX_SUCCESS
        : doc.getIndexStatus();
    return KnowledgeDocumentResponse.builder()
        .id(doc.getId())
        .knowledgeBaseId(doc.getKnowledgeBase().getId())
        .title(doc.getTitle())
        .sourceType(doc.getSourceType())
        .fileName(doc.getFileName())
        .fileType(doc.getFileType())
        .storagePath(doc.getStoragePath())
        .visibility(doc.getVisibility().name())
        .indexStatus(indexStatus)
        .indexMessage(doc.getIndexMessage())
        .indexProgress(doc.getIndexProgress() == null ? (INDEX_SUCCESS.equals(indexStatus) ? 100 : 0) : doc.getIndexProgress())
        .indexedAt(doc.getIndexedAt())
        .createdAt(doc.getCreatedAt())
        .build();
  }

  /** 将知识分块实体转换为响应 DTO。 */
  private KnowledgeChunkResponse toChunkResponse(KnowledgeChunk chunk, List<String> keywords) {
    return KnowledgeChunkResponse.builder()
        .id(chunk.getId())
        .knowledgeBaseId(chunk.getKnowledgeBase().getId())
        .knowledgeDocumentId(chunk.getKnowledgeDocument() == null ? null : chunk.getKnowledgeDocument().getId())
        .chunkIndex(chunk.getChunkIndex())
        .sectionTitle(chunk.getSectionTitle())
        .sectionPath(chunk.getSectionPath())
        .chunkType(chunk.getChunkType())
        .content(chunk.getContent())
        .embeddingDim(chunk.getEmbeddingDim())
        .keywords(keywords)
        .build();
  }

}
