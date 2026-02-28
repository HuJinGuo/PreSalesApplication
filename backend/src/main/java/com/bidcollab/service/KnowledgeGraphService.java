package com.bidcollab.service;

import com.bidcollab.dto.graph.KnowledgeGraphDetailChunk;
import com.bidcollab.dto.graph.KnowledgeGraphDetailDocument;
import com.bidcollab.dto.graph.KnowledgeGraphEdge;
import com.bidcollab.dto.graph.KnowledgeGraphNode;
import com.bidcollab.dto.graph.KnowledgeGraphNodeDetailResponse;
import com.bidcollab.dto.graph.KnowledgeGraphResponse;
import com.bidcollab.entity.KnowledgeChunk;
import com.bidcollab.entity.KnowledgeDocument;
import com.bidcollab.repository.KnowledgeChunkRepository;
import com.bidcollab.repository.KnowledgeGraphEdgeIndexRepository;
import com.bidcollab.repository.KnowledgeGraphEdgeIndexRepository.EdgeStatRow;
import com.bidcollab.repository.KnowledgeGraphNodeIndexRepository;
import com.bidcollab.repository.KnowledgeGraphNodeIndexRepository.NodeChunkStatRow;
import com.bidcollab.repository.KnowledgeGraphNodeIndexRepository.NodeStatRow;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeGraphService {
  private final KnowledgeAccessService knowledgeAccessService;
  private final KnowledgeGraphNodeIndexRepository graphNodeIndexRepository;
  private final KnowledgeGraphEdgeIndexRepository graphEdgeIndexRepository;
  private final KnowledgeChunkRepository chunkRepository;
  private final CurrentUserService currentUserService;

  public KnowledgeGraphService(KnowledgeAccessService knowledgeAccessService,
      KnowledgeGraphNodeIndexRepository graphNodeIndexRepository,
      KnowledgeGraphEdgeIndexRepository graphEdgeIndexRepository,
      KnowledgeChunkRepository chunkRepository,
      CurrentUserService currentUserService) {
    this.knowledgeAccessService = knowledgeAccessService;
    this.graphNodeIndexRepository = graphNodeIndexRepository;
    this.graphEdgeIndexRepository = graphEdgeIndexRepository;
    this.chunkRepository = chunkRepository;
    this.currentUserService = currentUserService;
  }

  public KnowledgeGraphResponse graph(Long kbId) {
    Long userId = currentUserService.getCurrentUserId();
    List<KnowledgeDocument> docs = knowledgeAccessService.listAccessibleDocuments(kbId, userId).stream()
        .limit(30)
        .toList();
    if (docs.isEmpty()) {
      return KnowledgeGraphResponse.builder().nodes(List.of()).edges(List.of()).build();
    }
    Set<Long> docIds = docs.stream().map(KnowledgeDocument::getId).collect(Collectors.toSet());
    List<NodeStatRow> nodeStats = graphNodeIndexRepository
        .findStatsByKnowledgeBaseIdAndKnowledgeDocumentIdIn(kbId, docIds);
    Map<Long, List<NodeStatRow>> nodeStatsByDoc = nodeStats.stream()
        .collect(Collectors.groupingBy(NodeStatRow::getKnowledgeDocumentId));
    List<EdgeStatRow> edgeStats = graphEdgeIndexRepository
        .findStatsByKnowledgeBaseIdAndKnowledgeDocumentIdIn(kbId, docIds);
    Map<Long, List<EdgeStatRow>> edgeStatsByDoc = edgeStats.stream()
        .collect(Collectors.groupingBy(EdgeStatRow::getKnowledgeDocumentId));

    Map<String, KnowledgeGraphNode> nodes = new LinkedHashMap<>();
    Map<String, KnowledgeGraphEdge> edges = new LinkedHashMap<>();
    Map<String, Integer> entityWeight = new HashMap<>();

    for (KnowledgeDocument doc : docs) {
      String docNodeId = "doc-" + doc.getId();
      nodes.put(docNodeId, KnowledgeGraphNode.builder()
          .id(docNodeId)
          .name(doc.getTitle())
          .category("DOCUMENT")
          .value(20)
          .build());

      Map<String, Integer> entities = aggregateNodeFrequency(nodeStatsByDoc.getOrDefault(doc.getId(), List.of()), 16);
      for (Map.Entry<String, Integer> entry : entities.entrySet()) {
        String entity = entry.getKey();
        int freq = entry.getValue();
        entityWeight.put(entity, entityWeight.getOrDefault(entity, 0) + freq);
        String entNodeId = "ent-" + entity;
        nodes.putIfAbsent(entNodeId, KnowledgeGraphNode.builder()
            .id(entNodeId)
            .name(entity)
            .category("DOMAIN_ENTITY")
            .value(Math.min(95, Math.max(12, freq * 2)))
            .build());
        edges.put(docNodeId + "->" + entNodeId, KnowledgeGraphEdge.builder()
            .source(docNodeId)
            .target(entNodeId)
            .label("mentions")
            .value(freq)
            .build());
      }

      for (EdgeStatRow row : edgeStatsByDoc.getOrDefault(doc.getId(), List.of())) {
        String source = row.getSourceNodeName() == null ? row.getSourceNodeKey() : row.getSourceNodeName();
        String target = row.getTargetNodeName() == null ? row.getTargetNodeKey() : row.getTargetNodeName();
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
          continue;
        }
        String sourceNode = "ent-" + source;
        String targetNode = "ent-" + target;
        nodes.putIfAbsent(sourceNode, KnowledgeGraphNode.builder().id(sourceNode).name(source).category("DOMAIN_ENTITY").value(10).build());
        nodes.putIfAbsent(targetNode, KnowledgeGraphNode.builder().id(targetNode).name(target).category("DOMAIN_ENTITY").value(10).build());
        String relationName = row.getRelationName() == null ? row.getRelationType() : row.getRelationName();
        String edgeKey = sourceNode + "->" + targetNode + "#" + relationName;
        KnowledgeGraphEdge old = edges.get(edgeKey);
        int rowFrequency = row.getFrequency() == null ? 1 : Math.max(1, row.getFrequency());
        int value = old == null ? rowFrequency : old.getValue() + rowFrequency;
        edges.put(edgeKey, KnowledgeGraphEdge.builder()
            .source(sourceNode)
            .target(targetNode)
            .label(relationName)
            .value(value)
            .build());
      }
    }

    for (Map.Entry<String, KnowledgeGraphNode> e : nodes.entrySet()) {
      if (!"DOMAIN_ENTITY".equals(e.getValue().getCategory())) {
        continue;
      }
      String name = e.getValue().getName();
      int weight = entityWeight.getOrDefault(name, 1);
      e.setValue(KnowledgeGraphNode.builder()
          .id(e.getValue().getId())
          .name(name)
          .category("DOMAIN_ENTITY")
          .value(Math.min(95, Math.max(14, weight * 3)))
          .build());
    }

    return KnowledgeGraphResponse.builder()
        .nodes(new ArrayList<>(nodes.values()))
        .edges(new ArrayList<>(edges.values()))
        .build();
  }

  public KnowledgeGraphNodeDetailResponse graphNodeDetail(Long kbId, String nodeId) {
    Long userId = currentUserService.getCurrentUserId();
    List<KnowledgeDocument> docs = knowledgeAccessService.listAccessibleDocuments(kbId, userId);
    if (docs.isEmpty()) {
      throw new EntityNotFoundException();
    }
    Map<Long, KnowledgeDocument> docById = docs.stream().collect(Collectors.toMap(KnowledgeDocument::getId, d -> d));
    Set<Long> docIds = docById.keySet();

    if (nodeId.startsWith("doc-")) {
      long docId = Long.parseLong(nodeId.substring(4));
      KnowledgeDocument doc = docById.get(docId);
      if (doc == null) {
        throw new EntityNotFoundException();
      }
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
          .toList();
      return KnowledgeGraphNodeDetailResponse.builder()
          .nodeId(nodeId)
          .nodeType("DOCUMENT")
          .name(doc.getTitle())
          .summary(snippet(doc.getContent(), 300))
          .relatedDocuments(List.of(KnowledgeGraphDetailDocument.builder().documentId(docId).title(doc.getTitle()).build()))
          .relatedKeywords(List.of())
          .relatedChunks(chunks)
          .build();
    }

    if (nodeId.startsWith("ent-")) {
      String entity = nodeId.substring(4).trim();
      List<NodeChunkStatRow> entityTerms = graphNodeIndexRepository
          .findStatsByKnowledgeBaseIdAndNodeKeyAndKnowledgeDocumentIdIn(kbId, entity, docIds);
      Map<Long, Integer> docHit = new HashMap<>();
      Map<Long, Integer> chunkHit = new HashMap<>();
      for (NodeChunkStatRow t : entityTerms) {
        docHit.put(t.getKnowledgeDocumentId(), docHit.getOrDefault(t.getKnowledgeDocumentId(), 0) + t.getFrequency());
        chunkHit.put(t.getKnowledgeChunkId(), chunkHit.getOrDefault(t.getKnowledgeChunkId(), 0) + t.getFrequency());
      }
      List<KnowledgeGraphDetailDocument> relatedDocs = docs.stream()
          .map(d -> KnowledgeGraphDetailDocument.builder()
              .documentId(d.getId())
              .title(d.getTitle())
              .hitCount(docHit.getOrDefault(d.getId(), 0))
              .build())
          .filter(d -> d.getHitCount() != null && d.getHitCount() > 0)
          .sorted(Comparator.comparingInt(KnowledgeGraphDetailDocument::getHitCount).reversed())
          .limit(10)
          .toList();
      List<KnowledgeGraphDetailChunk> relatedChunks = chunkRepository.findAllById(chunkHit.keySet()).stream()
          .map(c -> KnowledgeGraphDetailChunk.builder()
              .chunkId(c.getId())
              .documentId(c.getKnowledgeDocument().getId())
              .documentTitle(docById.get(c.getKnowledgeDocument().getId()).getTitle())
              .chunkIndex(c.getChunkIndex())
              .snippet(snippet(c.getContent(), 180))
              .hitCount(chunkHit.getOrDefault(c.getId(), 0))
              .build())
          .sorted(Comparator.comparingInt(KnowledgeGraphDetailChunk::getHitCount).reversed())
          .limit(10)
          .toList();
      return KnowledgeGraphNodeDetailResponse.builder()
          .nodeId(nodeId)
          .nodeType("DOMAIN_ENTITY")
          .name(entity)
          .summary("该实体在 " + relatedDocs.size() + " 个文档中出现，相关片段 " + relatedChunks.size() + " 条。")
          .relatedDocuments(relatedDocs)
          .relatedKeywords(List.of())
          .relatedChunks(relatedChunks)
          .build();
    }
    throw new IllegalArgumentException("Unsupported node: " + nodeId);
  }

  private Map<String, Integer> aggregateNodeFrequency(List<NodeStatRow> terms, int limit) {
    if (terms == null || terms.isEmpty()) {
      return Map.of();
    }
    Map<String, Integer> freq = new HashMap<>();
    for (NodeStatRow term : terms) {
      if (term == null || term.getNodeKey() == null) {
        continue;
      }
      int count = term.getFrequency() == null ? 1 : Math.max(1, term.getFrequency());
      freq.put(term.getNodeKey(), freq.getOrDefault(term.getNodeKey(), 0) + count);
    }
    return freq.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .limit(limit)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

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
}
