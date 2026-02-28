package com.bidcollab.service;

import com.bidcollab.ai.AiClient;
import com.bidcollab.ai.AiTraceContext;
import com.bidcollab.entity.KnowledgeChunk;
import com.bidcollab.entity.KnowledgeChunkTerm;
import com.bidcollab.entity.KnowledgeDocument;
import com.bidcollab.repository.KnowledgeGraphEdgeIndexRepository;
import com.bidcollab.repository.KnowledgeGraphNodeIndexRepository;
import com.bidcollab.repository.KnowledgeChunkRepository;
import com.bidcollab.repository.KnowledgeChunkTermRepository;
import com.bidcollab.util.TextChunker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeVectorIndexService {
  private static final java.util.regex.Pattern TOKEN_SPLIT = java.util.regex.Pattern.compile("[^\\p{IsHan}A-Za-z0-9]+");
  private static final Set<String> STOPWORDS = Set.of(
      "the", "and", "for", "with", "that", "this", "from", "have", "will", "shall",
      "项目", "系统", "进行", "以及", "我们", "你们", "他们", "可以", "通过", "工作", "方案", "服务",
      "每年", "每月", "每周", "每季度", "每半月", "每半年", "所示", "如下", "相关", "注意事项", "说明");

  private final TextChunker textChunker;
  private final AiClient aiClient;
  private final MilvusVectorService milvusVectorService;
  private final KnowledgeChunkRepository chunkRepository;
  private final KnowledgeChunkTermRepository chunkTermRepository;
  private final KnowledgeGraphNodeIndexRepository graphNodeIndexRepository;
  private final KnowledgeGraphEdgeIndexRepository graphEdgeIndexRepository;
  private final ExecutorService embeddingExecutor;

  public KnowledgeVectorIndexService(TextChunker textChunker,
      AiClient aiClient,
      MilvusVectorService milvusVectorService,
      KnowledgeChunkRepository chunkRepository,
      KnowledgeChunkTermRepository chunkTermRepository,
      KnowledgeGraphNodeIndexRepository graphNodeIndexRepository,
      KnowledgeGraphEdgeIndexRepository graphEdgeIndexRepository,
      @Qualifier("embeddingExecutor") ExecutorService embeddingExecutor) {
    this.textChunker = textChunker;
    this.aiClient = aiClient;
    this.milvusVectorService = milvusVectorService;
    this.chunkRepository = chunkRepository;
    this.chunkTermRepository = chunkTermRepository;
    this.graphNodeIndexRepository = graphNodeIndexRepository;
    this.graphEdgeIndexRepository = graphEdgeIndexRepository;
    this.embeddingExecutor = embeddingExecutor;
  }

  @Transactional
  public ReindexVectorResult rebuildVectors(KnowledgeDocument doc, String taskId, KnowledgeIndexStatusUpdater updater) {
    updater.update(doc.getId(), taskId, "RUNNING", "向量索引：删除旧索引", 8);
    milvusVectorService.deleteByDocumentId(doc.getId());
    graphEdgeIndexRepository.deleteByKnowledgeDocumentId(doc.getId());
    graphNodeIndexRepository.deleteByKnowledgeDocumentId(doc.getId());
    chunkTermRepository.deleteByKnowledgeDocumentId(doc.getId());
    chunkRepository.deleteByKnowledgeDocumentId(doc.getId());

    updater.update(doc.getId(), taskId, "RUNNING", "向量索引：切分文档中", 18);
    List<TextChunker.StructuredChunk> chunks = compactStructuredChunks(
        textChunker.splitBySectionsWithMetadata(doc.getContent(), 900, 140));

    @SuppressWarnings("unchecked")
    CompletableFuture<List<Double>>[] futures = new CompletableFuture[chunks.size()];
    updater.update(doc.getId(), taskId, "RUNNING", "向量索引：生成向量中", 28);
    for (int i = 0; i < chunks.size(); i++) {
      final int chunkIndex = i + 1;
      final String content = chunks.get(i).getContent();
      futures[i] = CompletableFuture.supplyAsync(() -> {
        AiTraceContext.AiTraceMeta meta = new AiTraceContext.AiTraceMeta(
            doc.getKnowledgeBase().getId(), doc.getId(), null, null, 0, "kb-reindex-embedding-" + chunkIndex);
        return AiTraceContext.with(meta, () -> aiClient.embedding(content));
      }, embeddingExecutor);
    }
    CompletableFuture.allOf(futures).join();

    updater.update(doc.getId(), taskId, "RUNNING", "向量索引：保存向量中", 56);
    List<KnowledgeChunk> savedChunks = new ArrayList<>();
    List<KnowledgeChunkTerm> keywordTerms = new ArrayList<>();
    List<MilvusVectorService.ChunkVectorRecord> vectorRecords = new ArrayList<>();
    int progressStep = Math.max(1, Math.max(1, chunks.size()) / 12);
    for (int i = 0; i < chunks.size(); i++) {
      TextChunker.StructuredChunk structured = chunks.get(i);
      List<Double> embedding = futures[i].join();
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
      savedChunks.add(chunk);
      extractKeywordFrequencyFallback(structured.getContent(), 12).forEach((k, v) -> keywordTerms.add(
          KnowledgeChunkTerm.builder()
              .knowledgeBase(doc.getKnowledgeBase())
              .knowledgeDocument(doc)
              .knowledgeChunk(chunk)
              .termType("KEYWORD")
              .termKey(k)
              .termName(k)
              .frequency(v)
              .build()));
      vectorRecords.add(new MilvusVectorService.ChunkVectorRecord(chunk.getId(), chunk.getChunkIndex(), embedding));
      if (i % progressStep == 0 || i == chunks.size() - 1) {
        int p = 56 + (int) (((i + 1) * 1.0 / Math.max(chunks.size(), 1)) * 28);
        updater.update(doc.getId(), taskId, "RUNNING", "向量索引：保存中 " + (i + 1) + "/" + chunks.size(), p);
      }
    }
    if (!keywordTerms.isEmpty()) {
      chunkTermRepository.saveAll(keywordTerms);
    }
    if (!vectorRecords.isEmpty()) {
      milvusVectorService.upsert(doc.getKnowledgeBase().getId(), doc.getId(), vectorRecords);
    }
    return new ReindexVectorResult(chunks, savedChunks);
  }

  private List<TextChunker.StructuredChunk> compactStructuredChunks(List<TextChunker.StructuredChunk> rawChunks) {
    if (rawChunks == null || rawChunks.isEmpty()) {
      return List.of();
    }
    List<TextChunker.StructuredChunk> result = new ArrayList<>();
    java.util.Set<String> dedup = new java.util.HashSet<>();
    for (TextChunker.StructuredChunk chunk : rawChunks) {
      if (chunk == null || chunk.getContent() == null) {
        continue;
      }
      String normalized = chunk.getContent().replaceAll("\\s+", " ").trim();
      if (normalized.length() < 20) {
        continue;
      }
      String key = ((chunk.getSectionPath() == null ? "" : chunk.getSectionPath()) + "|" + normalized).toLowerCase();
      if (!dedup.add(key)) {
        continue;
      }
      result.add(TextChunker.StructuredChunk.builder()
          .content(normalized)
          .sectionTitle(chunk.getSectionTitle())
          .sectionPath(chunk.getSectionPath())
          .chunkType(chunk.getChunkType())
          .build());
      if (result.size() >= 1200) {
        break;
      }
    }
    return result;
  }

  public record ReindexVectorResult(List<TextChunker.StructuredChunk> structuredChunks,
                                    List<KnowledgeChunk> savedChunks) {
  }

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
}
