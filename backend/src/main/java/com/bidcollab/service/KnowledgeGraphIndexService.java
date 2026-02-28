package com.bidcollab.service;

import com.bidcollab.entity.KnowledgeChunk;
import com.bidcollab.entity.KnowledgeDocument;
import com.bidcollab.entity.KnowledgeGraphEdgeIndex;
import com.bidcollab.entity.KnowledgeGraphNodeIndex;
import com.bidcollab.repository.KnowledgeGraphEdgeIndexRepository;
import com.bidcollab.repository.KnowledgeGraphNodeIndexRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeGraphIndexService {

  private final DomainLexiconService domainLexiconService;
  private final KnowledgeGraphNodeIndexRepository graphNodeIndexRepository;
  private final KnowledgeGraphEdgeIndexRepository graphEdgeIndexRepository;
  private final boolean domainLexiconEnabled;

  public KnowledgeGraphIndexService(DomainLexiconService domainLexiconService,
      KnowledgeGraphNodeIndexRepository graphNodeIndexRepository,
      KnowledgeGraphEdgeIndexRepository graphEdgeIndexRepository,
      @Value("${app.knowledge.domain-lexicon-enabled:true}") boolean domainLexiconEnabled) {
    this.domainLexiconService = domainLexiconService;
    this.graphNodeIndexRepository = graphNodeIndexRepository;
    this.graphEdgeIndexRepository = graphEdgeIndexRepository;
    this.domainLexiconEnabled = domainLexiconEnabled;
  }

  /**
   * 使用“人工领域词典 + 规则共现”构建图谱索引（不使用 LLM 抽实体）。
   */
  @Transactional
  public void rebuildGraphTerms(KnowledgeDocument doc,
      String taskId,
      List<KnowledgeChunk> savedChunks,
      KnowledgeIndexStatusUpdater updater) {
    graphEdgeIndexRepository.deleteByKnowledgeDocumentId(doc.getId());
    graphNodeIndexRepository.deleteByKnowledgeDocumentId(doc.getId());

    if (savedChunks == null || savedChunks.isEmpty()) {
      return;
    }
    if (!domainLexiconEnabled) {
      updater.update(doc.getId(), taskId, "RUNNING", "图谱索引：领域词典未启用，跳过", 98);
      return;
    }

    Map<String, Map<String, String>> lexicon = domainLexiconService
        .buildEffectiveLexiconAliasMap(doc.getKnowledgeBase().getId());
    Map<String, Map<String, String>> relationLabelMap = domainLexiconService.buildCategoryRelationLabelMap();
    if (lexicon == null || lexicon.isEmpty()) {
      updater.update(doc.getId(), taskId, "RUNNING", "图谱索引：词典为空，跳过", 98);
      return;
    }

    updater.update(doc.getId(), taskId, "RUNNING", "图谱索引：抽取实体关系中", 86);
    List<KnowledgeGraphNodeIndex> nodes = new ArrayList<>();
    List<KnowledgeGraphEdgeIndex> edges = new ArrayList<>();

    int progressStep = Math.max(1, savedChunks.size() / 10);
    for (int i = 0; i < savedChunks.size(); i++) {
      KnowledgeChunk chunk = savedChunks.get(i);
      String content = chunk.getContent();

      Map<String, Integer> entityFreq = extractDomainEntityFrequency(content, lexicon);
      for (Map.Entry<String, Integer> e : entityFreq.entrySet()) {
        String entityKey = e.getKey();
        nodes.add(KnowledgeGraphNodeIndex.builder()
            .knowledgeBase(doc.getKnowledgeBase())
            .knowledgeDocument(doc)
            .knowledgeChunk(chunk)
            .nodeKey(entityKey)
            .nodeName(readableEntityName(entityKey))
            .nodeType(entityType(entityKey) == null ? "DOMAIN_ENTITY" : entityType(entityKey))
            .frequency(e.getValue())
            .source("LEXICON_RULE")
            .build());
      }

      Map<String, Integer> relationFreq = extractEntityRelations(content, entityFreq.keySet(), relationLabelMap);
      for (Map.Entry<String, Integer> e : relationFreq.entrySet()) {
        String[] parts = e.getKey().split("\\|", 3);
        if (parts.length != 3) {
          continue;
        }
        String sourceKey = parts[0];
        String targetKey = parts[1];
        String relationName = parts[2];
        edges.add(KnowledgeGraphEdgeIndex.builder()
            .knowledgeBase(doc.getKnowledgeBase())
            .knowledgeDocument(doc)
            .knowledgeChunk(chunk)
            .sourceNodeKey(sourceKey)
            .sourceNodeName(readableEntityName(sourceKey))
            .targetNodeKey(targetKey)
            .targetNodeName(readableEntityName(targetKey))
            .relationType(relationName)
            .relationName(relationName)
            .frequency(e.getValue())
            .source("LEXICON_RULE")
            .build());
      }

      if (i % progressStep == 0 || i == savedChunks.size() - 1) {
        int p = 86 + (int) (((i + 1) * 1.0 / Math.max(savedChunks.size(), 1)) * 12);
        updater.update(doc.getId(), taskId, "RUNNING", "图谱索引：处理中 " + (i + 1) + "/" + savedChunks.size(), p);
      }
    }

    if (!nodes.isEmpty()) {
      graphNodeIndexRepository.saveAll(nodes);
    }
    if (!edges.isEmpty()) {
      graphEdgeIndexRepository.saveAll(edges);
    }
  }

  private Map<String, Integer> extractDomainEntityFrequency(String content, Map<String, Map<String, String>> lexicon) {
    if (content == null || content.isBlank()) {
      return Map.of();
    }
    String lower = content.toLowerCase();
    Map<String, Integer> result = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : lexicon.entrySet()) {
      String category = entry.getKey();
      Map<String, String> aliasMap = entry.getValue();
      if (aliasMap == null || aliasMap.isEmpty()) {
        continue;
      }
      for (Map.Entry<String, String> aliasEntry : aliasMap.entrySet()) {
        String alias = aliasEntry.getKey();
        String standardTerm = aliasEntry.getValue();
        int hit = countTokenHit(lower, alias == null ? null : alias.toLowerCase());
        if (hit > 0 && standardTerm != null && !standardTerm.isBlank()) {
          String key = category + "|" + standardTerm;
          result.put(key, result.getOrDefault(key, 0) + hit);
        }
      }
    }
    return result.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .limit(30)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  private Map<String, Integer> extractEntityRelations(
      String content,
      Set<String> entityKeys,
      Map<String, Map<String, String>> relationLabelMap) {
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
          String label = relationLabel(source, target, relationLabelMap);
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

  private String relationLabel(
      String sourceEntityKey,
      String targetEntityKey,
      Map<String, Map<String, String>> relationLabelMap) {
    String sourceType = entityType(sourceEntityKey);
    String targetType = entityType(targetEntityKey);
    if (sourceType == null || targetType == null) {
      return null;
    }
    Map<String, String> outgoing = relationLabelMap == null ? null : relationLabelMap.get(sourceType);
    if (outgoing == null || outgoing.isEmpty()) {
      return null;
    }
    return outgoing.get(targetType);
  }

  private String entityType(String entityKey) {
    if (entityKey == null) {
      return null;
    }
    String[] parts = entityKey.split("\\|", 2);
    return parts.length < 2 ? null : parts[0];
  }

  private String readableEntityName(String entityKey) {
    String[] parts = entityKey.split("\\|", 2);
    if (parts.length < 2) {
      return entityKey;
    }
    return parts[0] + ": " + parts[1];
  }

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
}
